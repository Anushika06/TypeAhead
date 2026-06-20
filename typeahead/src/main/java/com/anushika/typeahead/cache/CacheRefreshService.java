package com.anushika.typeahead.cache;

import com.anushika.typeahead.entity.SearchQuery;
import com.anushika.typeahead.repository.SearchQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Updates Redis Sorted Set caches after a successful PostgreSQL batch flush.
 *
 * <h2>Why here, not in {@link SuggestionCacheService}?</h2>
 * {@link SuggestionCacheService} manages full-prefix cache reads and writes
 * for the read path ({@code GET /suggest}).  This class handles the
 * post-flush <em>incremental update</em> of individual query scores across
 * multiple prefix keys — a distinct concern owned by the write pipeline.
 *
 * <h2>Algorithm for each flushed query</h2>
 * <ol>
 *   <li>Fetch the current row from PostgreSQL (post-commit values).</li>
 *   <li>Recompute ranking score:
 *       {@code score = log(total_count + 1) + log(trend_score + 1)}.</li>
 *   <li>Generate every prefix of length ≥ 3 up to the full query length.
 *       Example: {@code "google"} → {@code ["goo","goog","googl","google"]}.</li>
 *   <li>For each prefix, {@code ZADD prefix:<p> score query} — inserts the
 *       member if absent, or updates its score if already present.</li>
 *   <li>Trim the ZSET to the top {@value #TOP_K} members by score using
 *       {@code ZREMRANGEBYRANK prefix:<p> 0 -(TOP_K+1)} so lower-ranked
 *       suggestions are evicted automatically.</li>
 * </ol>
 *
 * <h2>Consistency guarantee</h2>
 * This method is called only after {@code BatchPersistenceService.persist()}
 * has committed its transaction.  If a Redis update fails, the error is
 * logged and swallowed — the next {@code GET /suggest} cache miss will
 * self-heal by re-reading from PostgreSQL and re-populating the key.
 */
@Service
public class CacheRefreshService {

    private static final Logger log = LoggerFactory.getLogger(CacheRefreshService.class);

    /** Matches the namespace in {@link SuggestionCacheService}. */
    private static final String KEY_NAMESPACE = "prefix:";

    /** Minimum prefix length to cache — mirrors MIN_PREFIX_LENGTH in SuggestionService. */
    private static final int MIN_PREFIX_LEN = 3;

    /** Maximum number of members to keep per prefix ZSET. */
    private static final int TOP_K = 10;

    private final SearchQueryRepository repository;
    private final StringRedisTemplate redisTemplate;

    public CacheRefreshService(SearchQueryRepository repository,
                               StringRedisTemplate redisTemplate) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Refreshes all Redis prefix ZSETs affected by the queries in the batch.
     *
     * <p>Must be called after {@code BatchPersistenceService.persist()} has
     * committed — only then do the DB rows reflect the incremented counts.
     *
     * @param queries set of normalised query strings that were flushed to DB
     */
    public void refreshAfterFlush(Set<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return;
        }

        // Fetch all updated rows in one DB round-trip
        List<SearchQuery> rows = repository.findAllById(queries);

        if (rows.isEmpty()) {
            log.warn("CacheRefresh: no DB rows found for {} queries", queries.size());
            return;
        }

        int prefixKeysUpdated = 0;

        for (SearchQuery row : rows) {
            try {
                prefixKeysUpdated += updatePrefixKeys(row);
            } catch (Exception ex) {
                // A failed update for one query must not block others
                log.warn("CacheRefresh: failed to update prefix keys for query='{}': {}",
                        row.getQuery(), ex.getMessage());
            }
        }

        log.info("CACHE REFRESH COMPLETED  queries={} prefixKeysUpdated={}",
                rows.size(), prefixKeysUpdated);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Updates every prefix ZSET for a single query and returns the number
     * of prefix keys that were touched.
     *
     * @param row fresh DB row for the query
     * @return number of prefix keys updated
     */
    private int updatePrefixKeys(SearchQuery row) {
        String query = row.getQuery();
        double score = computeScore(row.getTotalCount(), row.getTrendScore());
        List<String> prefixes = generatePrefixes(query);

        ZSetOperations<String, String> zOps = redisTemplate.opsForZSet();

        for (String prefix : prefixes) {
            String key = KEY_NAMESPACE + prefix;

            // ZADD key score member — inserts or updates the score in O(log N)
            zOps.add(key, query, score);

            // Trim: remove members with the lowest scores beyond TOP_K.
            // ZREMRANGEBYRANK key 0 -(TOP_K+1) removes ranks 0 … (size-TOP_K-1),
            // keeping exactly the TOP_K highest-scored members.
            zOps.removeRange(key, 0, -(TOP_K + 1));
        }

        log.debug("CacheRefresh: query='{}' score={} prefixes={}", query, score, prefixes);
        return prefixes.size();
    }

    /**
     * Recomputes the ranking score from fresh DB values.
     *
     * <p>Mirrors the formula in {@code SuggestionService.computeScore()} so
     * cached scores are always consistent with what the read path would compute.
     *
     * <pre>
     * score = log(total_count + 1) + log(trend_score + 1)
     * </pre>
     *
     * @param totalCount current total_count from PostgreSQL
     * @param trendScore current trend_score from PostgreSQL
     * @return rounded score (2 decimal places)
     */
    private double computeScore(long totalCount, double trendScore) {
        double score = Math.log(totalCount + 1) + Math.log(trendScore + 1);
        return Math.round(score * 100.0) / 100.0;
    }

    /**
     * Generates all prefixes of {@code query} with length in
     * [{@value #MIN_PREFIX_LEN}, query.length()], inclusive.
     *
     * <p>Example: {@code "google"} →
     * {@code ["goo", "goog", "googl", "google"]}.
     *
     * <p>Queries shorter than {@value #MIN_PREFIX_LEN} characters produce an
     * empty list — they cannot be retrieved via {@code GET /suggest} anyway.
     *
     * @param query normalised (lowercase, trimmed) query string
     * @return ordered list of prefix strings, shortest first
     */
    private List<String> generatePrefixes(String query) {
        List<String> prefixes = new ArrayList<>();
        for (int len = MIN_PREFIX_LEN; len <= query.length(); len++) {
            prefixes.add(query.substring(0, len));
        }
        return prefixes;
    }
}
