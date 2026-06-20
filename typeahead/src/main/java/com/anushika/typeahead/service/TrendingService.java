package com.anushika.typeahead.service;

import com.anushika.typeahead.dto.TrendingResponse;
import com.anushika.typeahead.entity.SearchQuery;
import com.anushika.typeahead.repository.SearchQueryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Returns the top 5 trending queries ranked by the logarithmic scoring formula.
 *
 * <h2>Formula</h2>
 * <pre>
 * score = log(total_count + 1) + log(trend_score + 1)
 * </pre>
 *
 * <p>Mirrors the formula in {@code SuggestionService} so trending rankings
 * are consistent with autocomplete rankings.  Ranking is delegated to
 * PostgreSQL so the result is already sorted when it arrives.
 *
 * <h2>Why a dedicated service?</h2>
 * Trending is a fundamentally different read concern from autocomplete:
 * no prefix filtering, full-table scan sorted by score, small fixed result set.
 * Keeping it separate prevents the suggestion service from accumulating
 * unrelated concerns.
 */
@Service
public class TrendingService {

    private final SearchQueryRepository repository;

    public TrendingService(SearchQueryRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the top 5 trending queries with their computed scores.
     *
     * @return ordered list, highest-trending first, at most 5 entries
     */
    public List<TrendingResponse> getTopTrending() {
        List<SearchQuery> rows = repository.findTopTrending();

        return rows.stream()
                .map(row -> new TrendingResponse(
                        row.getQuery(),
                        computeScore(row.getTotalCount(), row.getTrendScore()),
                        row.getTotalCount(),
                        row.getTrendScore()
                ))
                .toList();
    }

    /**
     * Ranking formula — mirrors {@code SuggestionService.computeScore()}.
     *
     * @param totalCount cumulative search count
     * @param trendScore decaying recency signal
     * @return score rounded to 2 decimal places
     */
    private double computeScore(long totalCount, double trendScore) {
        double score = Math.log(totalCount + 1) + Math.log(trendScore + 1);
        return Math.round(score * 100.0) / 100.0;
    }
}
