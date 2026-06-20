package com.anushika.typeahead.cache;

import com.anushika.typeahead.dto.SuggestionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Cache abstraction for typeahead suggestions backed by Redis Sorted Sets (ZSET).
 *
 * <h2>Data model</h2>
 * <pre>
 * Key:    prefix:{prefix}           e.g. prefix:goo
 * Type:   ZSET (Sorted Set)
 * Member: the suggestion string     e.g. "google", "google maps"
 * Score:  computed rank             e.g. 19.62
 *
 * Score formula (computed upstream, not here):
 *   score = log(total_count + 1) + log(trend_score + 1)
 * </pre>
 *
 * <h2>Why ZSET?</h2>
 * <ul>
 *   <li><b>ZADD</b> is idempotent — re-caching the same prefix simply
 *       overwrites scores in O(M log N).</li>
 *   <li><b>ZREVRANGE</b> returns top-k members already sorted
 *       highest-score-first in O(log N + k).</li>
 *   <li>Scores can be updated individually after a batch flush without
 *       rebuilding the entire key.</li>
 * </ul>
 *
 * <h2>Key naming convention</h2>
 * All keys use the {@code prefix:} namespace so they can be identified and
 * flushed independently of any other Redis data in the same instance.
 *
 * <h2>TTL</h2>
 * No TTL is set on cache entries.  Keys live until explicitly removed by
 * {@link #evictPrefix(String)}, which is called during PostgreSQL batch
 * updates.  Time-based expiry will be added in a later phase if needed.
 */
@Service
public class SuggestionCacheService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionCacheService.class);

    /** Redis key namespace for all prefix caches. */
    private static final String KEY_NAMESPACE = "prefix:";

    /** Maximum number of suggestions to store / retrieve per prefix. */
    private static final int TOP_K = 10;

    private final StringRedisTemplate redisTemplate;

    public SuggestionCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Writes a ranked list of suggestions for the given prefix into Redis.
     *
     * <p>Each {@link SuggestionResponse#query()} becomes a ZSET member and
     * {@link SuggestionResponse#score()} becomes its ZSET score.  The entire
     * key is replaced atomically by deleting first then bulk-adding via
     * {@code ZADD}, so a re-cache never leaves stale members behind.
     *
     * <p>No TTL is set.  Cache entries persist until explicitly evicted by
     * {@link #evictPrefix(String)}.
     *
     * @param prefix      lowercase search prefix (e.g. {@code "goo"})
     * @param suggestions ranked list from PostgreSQL, highest score first
     */
    public void cacheSuggestions(String prefix, List<SuggestionResponse> suggestions) {
        if (prefix == null || prefix.isBlank() || suggestions == null || suggestions.isEmpty()) {
            return;
        }

        String key = buildKey(prefix);

        try {
            // Delete first so a re-cache never leaves stale members behind
            redisTemplate.delete(key);

            ZSetOperations<String, String> zOps = redisTemplate.opsForZSet();
            for (SuggestionResponse suggestion : suggestions) {
                // ZADD key score member  (no TTL set — evicted explicitly)
                zOps.add(key, suggestion.query(), suggestion.score());
            }

            log.debug("Cached {} suggestions for prefix '{}' (no TTL)", suggestions.size(), prefix);
        } catch (Exception ex) {
            // Cache writes must never break the read path — log and continue
            log.warn("Failed to cache suggestions for prefix '{}': {}", prefix, ex.getMessage());
        }
    }

    /**
     * Retrieves the top-{@value #TOP_K} suggestions for the given prefix from Redis.
     *
     * <p>Uses {@code ZREVRANGEBYSCORE} semantics via
     * {@link ZSetOperations#reverseRangeWithScores} so members are returned
     * highest-score-first, matching the PostgreSQL ordering.
     *
     * @param prefix lowercase search prefix (e.g. {@code "goo"})
     * @return ordered list of suggestions, or an empty list on a cache miss
     */
    public List<SuggestionResponse> getSuggestions(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Collections.emptyList();
        }

        String key = buildKey(prefix);

        try {
            // ZREVRANGE key 0 (TOP_K - 1) WITHSCORES — highest score first
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, TOP_K - 1);

            if (tuples == null || tuples.isEmpty()) {
                log.debug("Cache miss for prefix '{}'", prefix);
                return Collections.emptyList();
            }

            List<SuggestionResponse> results = tuples.stream()
                    .filter(t -> t.getValue() != null && t.getScore() != null)
                    .map(t -> new SuggestionResponse(t.getValue(), t.getScore()))
                    .toList();

            log.debug("Cache hit for prefix '{}': {} suggestions", prefix, results.size());
            return results;

        } catch (Exception ex) {
            // Cache reads must never break the read path — return empty = miss
            log.warn("Failed to read cache for prefix '{}': {}", prefix, ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Removes the ZSET key for the given prefix from Redis.
     *
     * <p>Intended for use after a PostgreSQL batch flush to force a fresh
     * cache population on the next request.
     *
     * @param prefix lowercase search prefix (e.g. {@code "goo"})
     */
    public void evictPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }

        String key = buildKey(prefix);

        try {
            Boolean deleted = redisTemplate.delete(key);
            log.debug("Evicted cache key '{}': {}", key, Boolean.TRUE.equals(deleted) ? "deleted" : "not found");
        } catch (Exception ex) {
            log.warn("Failed to evict cache for prefix '{}': {}", prefix, ex.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds the canonical Redis key for a prefix.
     *
     * <p>Keys are always lower-cased to match the case-insensitive behaviour
     * of the suggestion API.
     *
     * @param prefix raw search prefix
     * @return Redis key, e.g. {@code "prefix:goo"}
     */
    private String buildKey(String prefix) {
        return KEY_NAMESPACE + prefix.toLowerCase();
    }
}
