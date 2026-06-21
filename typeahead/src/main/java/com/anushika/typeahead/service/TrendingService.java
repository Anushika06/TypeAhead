package com.anushika.typeahead.service;

import com.anushika.typeahead.dto.TrendingResponse;
import com.anushika.typeahead.entity.SearchQuery;
import com.anushika.typeahead.repository.SearchQueryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Returns the top trending queries ranked by the logarithmic scoring formula.
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
     * Ranking formula.
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
