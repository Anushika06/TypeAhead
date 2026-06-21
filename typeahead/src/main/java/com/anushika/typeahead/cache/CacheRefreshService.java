package com.anushika.typeahead.cache;

import com.anushika.typeahead.entity.SearchQuery;
import com.anushika.typeahead.repository.SearchQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import com.anushika.typeahead.service.RankingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Updates Redis Sorted Set caches after a successful PostgreSQL batch flush.
 */
@Service
public class CacheRefreshService {

    private static final Logger log = LoggerFactory.getLogger(CacheRefreshService.class);

    private final SearchQueryRepository repository;
    private final ConsistentHashRing    ring;
    private final StringRedisTemplate   redisTemplate;
    private final RankingService        rankingService;

    public CacheRefreshService(SearchQueryRepository repository,
                               ConsistentHashRing ring,
                               StringRedisTemplate redisTemplate,
                               RankingService rankingService) {
        this.repository    = repository;
        this.ring          = ring;
        this.redisTemplate = redisTemplate;
        this.rankingService = rankingService;
    }



    /**
     * Refreshes all Redis prefix ZSETs affected by the queries in the batch.
     *
     * @param queries set of normalised query strings that were flushed to DB
     */
    public void refreshAfterFlush(Set<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return;
        }

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



    /**
     * Updates every prefix ZSET for a single query and returns the number of prefix keys that were touched.
     *
     * @param row fresh DB row for the query
     * @return number of prefix keys updated
     */
    private int updatePrefixKeys(SearchQuery row) {
        String query  = row.getQuery();
        double score  = rankingService.computeScore(row.getTotalCount(), row.getTrendScore());
        List<String> prefixes = generatePrefixes(query);

        ZSetOperations<String, String> zOps = redisTemplate.opsForZSet();

        for (String prefix : prefixes) {
            // Route through the ring — each prefix may land on a different logical node
            String node = ring.getNode(prefix);
            log.info("CACHE NODE ASSIGNED  prefix={}  node={}", prefix, node);

            String key = CacheConstants.PREFIX_NAMESPACE + prefix;

            // ZADD key score member — inserts or updates the score in O(log N)
            zOps.add(key, query, score);

            // Trim: remove members with the lowest scores beyond TOP_K.
            // ZREMRANGEBYRANK key 0 -(TOP_K+1) removes ranks 0 … (size-TOP_K-1),
            // keeping exactly the TOP_K highest-scored members.
            zOps.removeRange(key, 0, -(CacheConstants.TOP_K + 1));
        }

        log.debug("CacheRefresh: query='{}' score={} prefixes={}", query, score, prefixes);
        return prefixes.size();
    }



    /**
     * Generates all prefixes of query with length in [MIN_PREFIX_LEN, query.length()], inclusive.
     *
     * @param query normalised (lowercase, trimmed) query string
     * @return ordered list of prefix strings, shortest first
     */
    private List<String> generatePrefixes(String query) {
        List<String> prefixes = new ArrayList<>();
        for (int len = CacheConstants.MIN_PREFIX_LENGTH; len <= query.length(); len++) {
            prefixes.add(query.substring(0, len));
        }
        return prefixes;
    }
}
