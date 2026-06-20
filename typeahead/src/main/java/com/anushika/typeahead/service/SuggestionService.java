package com.anushika.typeahead.service;

import com.anushika.typeahead.cache.CacheMetrics;
import com.anushika.typeahead.cache.SuggestionCacheService;
import com.anushika.typeahead.dto.SuggestionResponse;
import com.anushika.typeahead.repository.SearchQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);

    private static final int MIN_PREFIX_LENGTH = 3;
    private static final int MAX_SUGGESTIONS   = 10;

    private final SearchQueryRepository searchQueryRepository;
    private final SuggestionCacheService cacheService;
    private final CacheMetrics cacheMetrics;
    private final MetricsService metricsService;

    public SuggestionService(SearchQueryRepository searchQueryRepository,
                             SuggestionCacheService cacheService,
                             CacheMetrics cacheMetrics,
                             MetricsService metricsService) {
        this.searchQueryRepository = searchQueryRepository;
        this.cacheService = cacheService;
        this.cacheMetrics = cacheMetrics;
        this.metricsService = metricsService;
    }

    /**
     * Returns up to 10 suggestions for the given prefix using Cache Aside:
     *
     * <ol>
     *   <li>Normalise prefix (trim + lowercase).</li>
     *   <li>Return immediately if prefix is shorter than MIN_PREFIX_LENGTH.</li>
     *   <li>Check Redis — on hit, return cached results directly.</li>
     *   <li>On miss, query PostgreSQL, compute scores, populate Redis, return.</li>
     * </ol>
     *
     * Ranking formula:
     *   score = log(total_count + 1) + log(trend_score + 1)
     *
     * The API contract is preserved: callers always receive a
     * {@code List<SuggestionResponse>} regardless of whether the result
     * came from cache or database.
     */
    public List<SuggestionResponse> getSuggestions(String prefix) {
        if (prefix == null || prefix.trim().length() < MIN_PREFIX_LENGTH) {
            return List.of();
        }

        // Normalise: trim whitespace and lowercase so "Goo" and "goo" share the same key
        String normalised = prefix.trim().toLowerCase();

        // ── 1. Cache check ────────────────────────────────────────────────────
        List<SuggestionResponse> cached = cacheService.getSuggestions(normalised);
        if (!cached.isEmpty()) {
            log.info("CACHE HIT  prefix={}", normalised);
            cacheMetrics.recordCacheHit();
            return cached;
        }

        log.info("CACHE MISS prefix={}", normalised);
        cacheMetrics.recordCacheMiss();

        // ── 2. Database fallback ──────────────────────────────────────────────
        metricsService.recordDbRead();
        List<SuggestionResponse> results = searchQueryRepository
                .findCandidatesByPrefix(normalised)
                .stream()
                .map(sq -> new SuggestionResponse(
                        sq.getQuery(),
                        computeScore(sq.getTotalCount(), sq.getTrendScore())
                ))
                .sorted(Comparator.comparingDouble(SuggestionResponse::score).reversed())
                .limit(MAX_SUGGESTIONS)
                .toList();

        // ── 3. Populate cache ─────────────────────────────────────────────────
        // Fire-and-forget: cacheSuggestions swallows its own exceptions so a
        // Redis write failure never affects the response returned to the caller.
        cacheService.cacheSuggestions(normalised, results);

        return results;
    }

    /**
     * Ranking formula. Logarithmic scaling prevents high historical counts
     * from permanently dominating trending queries.
     *
     * ranking_score is intentionally not persisted — computed here at read time.
     */
    private double computeScore(long totalCount, double trendScore) {
        double score = Math.log(totalCount + 1) + Math.log(trendScore + 1);
        return Math.round(score * 100.0) / 100.0;
    }
}
