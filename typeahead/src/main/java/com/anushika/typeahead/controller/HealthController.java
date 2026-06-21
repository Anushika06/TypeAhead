package com.anushika.typeahead.controller;

import com.anushika.typeahead.repository.SearchQueryRepository;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;


@Tag(name = "Health", description = "System health check endpoints")
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
    @Operation(
            summary = "Database health check",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation")
            }
    )
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
     * Opens a connection to Redis and sends a PING command.
     */
    @Operation(
            summary = "Redis health check",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation"),
                    @ApiResponse(responseCode = "503", description = "Redis connection failed")
            }
    )
    @GetMapping("/redis")
    public ResponseEntity<Map<String, String>> checkRedis() {
        try {
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
