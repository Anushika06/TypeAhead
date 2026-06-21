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
 */
@Service
public class SuggestionCacheService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionCacheService.class);

    private final ConsistentHashRing  ring;
    private final StringRedisTemplate redisTemplate;

    public SuggestionCacheService(ConsistentHashRing ring,
                                  StringRedisTemplate redisTemplate) {
        this.ring          = ring;
        this.redisTemplate = redisTemplate;
    }



    /**
     * Writes a ranked list of suggestions for the given prefix into Redis.
     *
     * @param prefix      lowercase search prefix
     * @param suggestions ranked list from PostgreSQL, highest score first
     */
    public void cacheSuggestions(String prefix, List<SuggestionResponse> suggestions) {
        if (prefix == null || prefix.isBlank() || suggestions == null || suggestions.isEmpty()) {
            return;
        }

        String node = ring.getNode(prefix);
        log.info("CACHE NODE ASSIGNED  prefix={}  node={}", prefix, node);

        String key = buildKey(prefix);

        try {
            // Delete first so a re-cache never leaves stale members behind
            redisTemplate.delete(key);

            ZSetOperations<String, String> zOps = redisTemplate.opsForZSet();
            for (SuggestionResponse suggestion : suggestions) {
                // ZADD key score member  (no TTL set — evicted explicitly)
                zOps.add(key, suggestion.query(), suggestion.score());
            }

            log.debug("[{}] cached {} suggestions for prefix '{}' (no TTL)",
                    node, suggestions.size(), prefix);
        } catch (Exception ex) {
            // Cache writes must never break the read path — log and continue
            log.warn("[{}] failed to cache suggestions for prefix '{}': {}",
                    node, prefix, ex.getMessage());
        }
    }

    /**
     * Retrieves the top suggestions for the given prefix from Redis.
     *
     * @param prefix lowercase search prefix
     * @return ordered list of suggestions, or an empty list on a cache miss
     */
    public List<SuggestionResponse> getSuggestions(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Collections.emptyList();
        }

        String node = ring.getNode(prefix);
        log.info("CACHE NODE ASSIGNED  prefix={}  node={}", prefix, node);

        String key = buildKey(prefix);

        try {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, CacheConstants.TOP_K - 1);

            if (tuples == null || tuples.isEmpty()) {
                log.debug("[{}] cache miss for prefix '{}'", node, prefix);
                return Collections.emptyList();
            }

            List<SuggestionResponse> results = tuples.stream()
                    .filter(t -> t.getValue() != null && t.getScore() != null)
                    .map(t -> new SuggestionResponse(t.getValue(), t.getScore()))
                    .toList();

            log.debug("[{}] cache hit for prefix '{}': {} suggestions",
                    node, prefix, results.size());
            return results;

        } catch (Exception ex) {
            // Cache reads must never break the read path — return empty = miss
            log.warn("[{}] failed to read cache for prefix '{}': {}",
                    node, prefix, ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Removes the ZSET key for the given prefix from Redis.
     *
     * @param prefix lowercase search prefix
     */
    public void evictPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }

        String node = ring.getNode(prefix);
        log.info("CACHE NODE ASSIGNED  prefix={}  node={}", prefix, node);

        String key = buildKey(prefix);

        try {
            Boolean deleted = redisTemplate.delete(key);
            log.debug("[{}] evicted cache key '{}': {}", node, key,
                    Boolean.TRUE.equals(deleted) ? "deleted" : "not found");
        } catch (Exception ex) {
            log.warn("[{}] failed to evict cache for prefix '{}': {}",
                    node, prefix, ex.getMessage());
        }
    }



    /**
     * Builds the canonical Redis key for a prefix.
     *
     * @param prefix raw search prefix
     * @return Redis key
     */
    private String buildKey(String prefix) {
        return CacheConstants.PREFIX_NAMESPACE + prefix.toLowerCase();
    }
}
