package com.anushika.typeahead.controller;

import com.anushika.typeahead.dto.TrendingResponse;
import com.anushika.typeahead.service.TrendingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-path controller: returns top trending search queries.
 *
 * <h2>Contract</h2>
 * <pre>
 * GET /trending
 *
 * 200 OK
 * [
 *   { "query": "google", "score": 19.62, "totalCount": 32532, "trendScore": 10224.0 },
 *   ...
 * ]
 * </pre>
 *
 * <p>Returns the top 5 queries ranked by
 * {@code log(total_count + 1) + log(trend_score + 1)}.
 * Results are always sorted highest-trending-first.
 *
 * <p>No business logic lives here — all ranking is delegated to
 * {@link TrendingService}.
 */
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
    @GetMapping("/trending")
    public ResponseEntity<List<TrendingResponse>> trending() {
        return ResponseEntity.ok(trendingService.getTopTrending());
    }
}
