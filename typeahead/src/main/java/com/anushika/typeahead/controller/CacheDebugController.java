package com.anushika.typeahead.controller;

import com.anushika.typeahead.service.MetricsService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Debugging and observability endpoints for the Redis cache layer.
 *
 * <p>These endpoints exist purely for assignment demonstration and
 * operational insight — they are NOT part of the public suggestion API.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /cache/debug?prefix=goo} — inspect a single ZSET key</li>
 *   <li>{@code GET /metrics}               — full system metrics snapshot</li>
 * </ul>
 */
@RestController
@RequestMapping
public class CacheDebugController {

    /** Key namespace must match SuggestionCacheService.KEY_NAMESPACE */
    private static final String KEY_NAMESPACE = "prefix:";

    private final StringRedisTemplate redisTemplate;
    private final MetricsService metricsService;

    public CacheDebugController(StringRedisTemplate redisTemplate,
                                MetricsService metricsService) {
        this.redisTemplate = redisTemplate;
        this.metricsService = metricsService;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /cache/debug?prefix=<prefix>
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Inspects the Redis ZSET for the given prefix and reports whether it
     * exists and how many suggestions are cached.
     *
     * <p>Response when the key exists:
     * <pre>
     * { "prefix": "goo", "exists": true, "suggestionCount": 10 }
     * </pre>
     *
     * <p>Response when the key is absent:
     * <pre>
     * { "prefix": "goo", "exists": false }
     * </pre>
     *
     * @param prefix the search prefix to inspect (case-insensitive)
     */
    @GetMapping("/cache/debug")
    public ResponseEntity<Map<String, Object>> debugCache(
            @RequestParam("prefix") String prefix) {

        String normalised = prefix.trim().toLowerCase();
        String key = KEY_NAMESPACE + normalised;

        // ZCARD returns the number of members in the sorted set, or 0 if absent
        Long count = redisTemplate.opsForZSet().size(key);

        // LinkedHashMap preserves insertion order for a predictable JSON field sequence
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prefix", normalised);

        if (count != null && count > 0) {
            body.put("exists", true);
            body.put("suggestionCount", count);
        } else {
            body.put("exists", false);
        }

        return ResponseEntity.ok(body);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /metrics
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns a full snapshot of system-wide metrics accumulated since the
     * last application restart.
     *
     * <p>Response:
     * <pre>
     * {
     *   "cacheHits":             1200,
     *   "cacheMisses":            140,
     *   "cacheHitRate":          89.5,
     *   "dbReads":                300,
     *   "dbWrites":                40,
     *   "streamEventsPublished":  500,
     *   "streamEventsConsumed":   500,
     *   "batchFlushes":            12,
     *   "avgFlushSize":           412
     * }
     * </pre>
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        return ResponseEntity.ok(metricsService.getSnapshot());
    }
}
