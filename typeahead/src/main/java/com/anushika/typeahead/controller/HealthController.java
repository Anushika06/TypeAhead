package com.anushika.typeahead.controller;

import com.anushika.typeahead.repository.SearchQueryRepository;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight health-check endpoints.
 *
 * <p>Kept separate from the suggestion API so ops tooling can probe
 * infrastructure without touching any business logic.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    private final SearchQueryRepository searchQueryRepository;
    private final RedisConnectionFactory redisConnectionFactory;

    public HealthController(SearchQueryRepository searchQueryRepository,
                            RedisConnectionFactory redisConnectionFactory) {
        this.searchQueryRepository = searchQueryRepository;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    /**
     * GET /health/db
     *
     * Verifies that the application can reach PostgreSQL and that
     * the search_queries table is accessible.
     *
     * Response: { "status": "ok", "count": 128810 }
     */
    @GetMapping("/db")
    public ResponseEntity<Map<String, Object>> checkDb() {
        long count = searchQueryRepository.count();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "count", count
        ));
    }

    /**
     * GET /health/redis
     *
     * <p>Opens a connection to Redis and sends a PING command.
     * Returns {@code {"status":"ok"}} on success, or
     * {@code {"status":"error","detail":"..."}} with HTTP 503
     * if the connection cannot be established.
     *
     * <p>This is a connectivity probe only — no caching logic is exercised.
     */
    @GetMapping("/redis")
    public ResponseEntity<Map<String, String>> checkRedis() {
        try {
            // Borrow a connection and issue PING — throws on any connectivity failure
            redisConnectionFactory.getConnection().ping();
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception ex) {
            return ResponseEntity
                    .status(503)
                    .body(Map.of(
                            "status", "error",
                            "detail", ex.getMessage()
                    ));
        }
    }
}
