package com.anushika.typeahead.controller;

import com.anushika.typeahead.cache.CacheConstants;
import com.anushika.typeahead.cache.ConsistentHashRing;
import com.anushika.typeahead.service.MetricsService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

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
 *
 * <h2>Routing consistency</h2>
 * {@code GET /cache/debug} resolves the node via the same
 * {@link ConsistentHashRing#getNode(String)} call used by
 * {@link com.anushika.typeahead.cache.SuggestionCacheService} and
 * {@link com.anushika.typeahead.cache.CacheRefreshService}.
 * The {@code assignedNode} field therefore reflects the actual routing used
 * during live cache operations — there is no special-case logic.
 */
@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping
public class CacheDebugController {


    private final ConsistentHashRing  hashRing;
    private final StringRedisTemplate redisTemplate;
    private final MetricsService      metricsService;

    public CacheDebugController(ConsistentHashRing hashRing,
                                StringRedisTemplate redisTemplate,
                                MetricsService metricsService) {
        this.hashRing      = hashRing;
        this.redisTemplate = redisTemplate;
        this.metricsService = metricsService;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /cache/debug?prefix=<prefix>
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Inspects the Redis ZSET for a given prefix and shows which consistent-hash
     * ring node is responsible for storing it.
     *
     * <p>Node resolution uses {@link ConsistentHashRing#getNode(String)} — the
     * same call made by all live cache operations — ensuring the {@code assignedNode}
     * in this response always matches the routing used in production.
     *
     * <p>Response when the key exists:
     * <pre>
     * {
     *   "prefix":          "goo",
     *   "assignedNode":    "redis-node-2",
     *   "cacheHit":        true,
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
    @Tag(name = "Cache Debug", description = "Endpoints for inspecting cache and hashing ring")
    @Operation(
            summary = "Inspect cache routing",
            description = "Debug endpoints demonstrating logical cache node\nassignment using consistent hashing.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation")
            }
    )
    @GetMapping("/cache/debug")
    public ResponseEntity<Map<String, Object>> debugCache(
            @Parameter(description = "Prefix to inspect")
            @RequestParam("prefix") String prefix) {

        String normalised    = prefix.trim().toLowerCase();
        String key           = CacheConstants.PREFIX_NAMESPACE + normalised;
        String assignedNode  = hashRing.getNode(normalised);

        // ZCARD returns the member count of the sorted set, or null/0 if absent
        Long count      = redisTemplate.opsForZSet().size(key);
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
    @Tag(name = "Cache Debug")
    @Operation(
            summary = "Inspect consistent hash ring",
            description = "Debug endpoints demonstrating logical cache node\nassignment using consistent hashing.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation")
            }
    )
    @GetMapping("/cache/ring")
    public ResponseEntity<Map<String, Object>> ringView() {
        List<String> nodes            = hashRing.getPhysicalNodes();
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
     *   "batchFlushCount":         12,
     *   "avgFlushSize":           412
     * }
     * </pre>
     */
    @Tag(name = "Metrics", description = "System metrics endpoints")
    @Operation(
            summary = "Get system metrics",
            description = "Returns cache, stream, database,\nand batch-processing statistics.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation")
            }
    )
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        return ResponseEntity.ok(metricsService.getSnapshot());
    }
}
