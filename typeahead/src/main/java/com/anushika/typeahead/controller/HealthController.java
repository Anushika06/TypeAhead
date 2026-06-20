package com.anushika.typeahead.controller;

import com.anushika.typeahead.repository.SearchQueryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final SearchQueryRepository searchQueryRepository;

    public HealthController(SearchQueryRepository searchQueryRepository) {
        this.searchQueryRepository = searchQueryRepository;
    }

    /**
     * Temporary connectivity check.
     * Verifies that the application can reach PostgreSQL and that
     * the search_queries table is accessible.
     *
     * GET /health/db
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
}
