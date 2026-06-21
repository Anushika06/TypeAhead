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



    /**
     * Inspects the Redis ZSET for a given prefix and shows which consistent-hash
     * ring node is responsible for storing it.
     *
     * @param prefix the search prefix to inspect
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



    /**
     * Visualises the consistent hashing ring: node count, virtual node count,
     * load distribution, and sample key→node mappings.
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



    /**
     * Returns a full snapshot of system-wide metrics accumulated since the last application restart.
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
