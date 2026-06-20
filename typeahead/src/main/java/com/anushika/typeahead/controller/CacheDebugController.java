package com.anushika.typeahead.controller;

import com.anushika.typeahead.cache.ConsistentHashRing;
import com.anushika.typeahead.service.MetricsService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Debugging, observability, and consistent-hashing demonstration endpoints.
 *
 * <p>These endpoints exist purely for assignment demonstration and
 * operational insight — they are NOT part of the public suggestion API.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /cache/debug?prefix=goo} — inspect ZSET + show assigned node</li>
 *   <li>{@code GET /cache/ring}             — visualise the consistent hashing ring</li>
 *   <li>{@code GET /metrics}               — full system metrics snapshot</li>
 * </ul>
 */
@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping
public class CacheDebugController {

    /** Key namespace must match SuggestionCacheService.KEY_NAMESPACE */
    private static final String KEY_NAMESPACE = "prefix:";

    private final StringRedisTemplate redisTemplate;
    private final MetricsService metricsService;
    private final ConsistentHashRing hashRing;

    public CacheDebugController(StringRedisTemplate redisTemplate,
                                MetricsService metricsService,
                                ConsistentHashRing hashRing) {
        this.redisTemplate = redisTemplate;
        this.metricsService = metricsService;
        this.hashRing = hashRing;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /cache/debug?prefix=<prefix>
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Inspects the Redis ZSET for a given prefix and shows which consistent-hash
     * ring node is responsible for storing it.
     *
     * <p>Response when the key exists:
     * <pre>
     * {
     *   "prefix":       "goo",
     *   "assignedNode": "redis-node-2",
     *   "cacheHit":     true,
     *   "suggestionCount": 10
     * }
     * </pre>
     *
     * <p>Response when the key is absent:
     * <pre>
     * {
     *   "prefix":       "goo",
     *   "assignedNode": "redis-node-2",
     *   "cacheHit":     false
     * }
     * </pre>
     *
     * @param prefix the search prefix to inspect (case-insensitive)
     */
    @GetMapping("/cache/debug")
    public ResponseEntity<Map<String, Object>> debugCache(
            @RequestParam("prefix") String prefix) {

        String normalised  = prefix.trim().toLowerCase();
        String key         = KEY_NAMESPACE + normalised;
        String assignedNode = hashRing.getNode(normalised);

        // ZCARD returns the member count of the sorted set, or null/0 if absent
        Long count = redisTemplate.opsForZSet().size(key);
        boolean cacheHit = count != null && count > 0;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prefix",       normalised);
        body.put("assignedNode", assignedNode);
        body.put("cacheHit",     cacheHit);
        if (cacheHit) {
            body.put("suggestionCount", count);
        }

        return ResponseEntity.ok(body);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /cache/ring
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Visualises the consistent hashing ring: node count, virtual node count,
     * load distribution, and sample key→node mappings.
     *
     * <p>Response shape:
     * <pre>
     * {
     *   "physicalNodes":          3,
     *   "virtualNodesPerPhysical": 150,
     *   "totalRingPositions":     450,
     *   "nodes": [
     *     { "name": "redis-node-1", "virtualNodes": 150, "ownershipPercent": 33.3 },
     *     ...
     *   ],
     *   "sampleMappings": {
     *     "goo":    "redis-node-2",
     *     "iph":    "redis-node-1",
     *     "chatgpt": "redis-node-3",
     *     ...
     *   }
     * }
     * </pre>
     */
    @GetMapping("/cache/ring")
    public ResponseEntity<Map<String, Object>> ringView() {
        List<String> nodes           = hashRing.getPhysicalNodes();
        Map<String, Double> ownership = hashRing.getOwnershipPercentages();

        // Build the per-node detail list
        List<Map<String, Object>> nodeDetails = new ArrayList<>();
        for (String node : nodes) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("name",             node);
            detail.put("virtualNodes",     ConsistentHashRing.VIRTUAL_NODES);
            detail.put("ownershipPercent", ownership.getOrDefault(node, 0.0));
            nodeDetails.add(detail);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("physicalNodes",           nodes.size());
        body.put("virtualNodesPerPhysical", ConsistentHashRing.VIRTUAL_NODES);
        body.put("totalRingPositions",      hashRing.getTotalRingPositions());
        body.put("nodes",                   nodeDetails);
        body.put("sampleMappings",          hashRing.getSampleMappings());

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
