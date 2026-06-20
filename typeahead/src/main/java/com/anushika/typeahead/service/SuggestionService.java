package com.anushika.typeahead.service;

import com.anushika.typeahead.dto.SuggestionResponse;
import com.anushika.typeahead.repository.SearchQueryRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class SuggestionService {

    private static final int MIN_PREFIX_LENGTH = 3;
    private static final int MAX_SUGGESTIONS   = 10;

    private final SearchQueryRepository searchQueryRepository;

    public SuggestionService(SearchQueryRepository searchQueryRepository) {
        this.searchQueryRepository = searchQueryRepository;
    }

    /**
     * Returns up to 10 suggestions for the given prefix, ranked by:
     *
     *   score = log(total_count + 1) + log(trend_score + 1)
     *
     * Returns an empty list immediately if the prefix is null or shorter
     * than MIN_PREFIX_LENGTH (3 characters).
     */
    public List<SuggestionResponse> getSuggestions(String prefix) {
        if (prefix == null || prefix.trim().length() < MIN_PREFIX_LENGTH) {
            return List.of();
        }

        return searchQueryRepository
                .findCandidatesByPrefix(prefix.trim())
                .stream()
                .map(sq -> new SuggestionResponse(
                        sq.getQuery(),
                        computeScore(sq.getTotalCount(), sq.getTrendScore())
                ))
                .sorted(Comparator.comparingDouble(SuggestionResponse::score).reversed())
                .limit(MAX_SUGGESTIONS)
                .toList();
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
