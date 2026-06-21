package com.anushika.typeahead.controller;

import com.anushika.typeahead.dto.TrendingResponse;
import com.anushika.typeahead.service.TrendingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * Read-path controller: returns top trending search queries.
 */
@Tag(name = "Trending", description = "Trending search query endpoints")
@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class TrendingController {

    private final TrendingService trendingService;

    public TrendingController(TrendingService trendingService) {
        this.trendingService = trendingService;
    }

    /**
     * Returns the current top-5 trending queries.
     *
     * @return ranked list of {@link TrendingResponse} DTOs
     */
    @Operation(
            summary = "Get top trending searches",
            description = "Returns highest ranked searches based on:\n\nlog(total_count + 1)\n+\nlog(trend_score + 1)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation")
            }
    )
    @GetMapping("/trending")
    public ResponseEntity<List<TrendingResponse>> trending() {
        return ResponseEntity.ok(trendingService.getTopTrending());
    }
}
