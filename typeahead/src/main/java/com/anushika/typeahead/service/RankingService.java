package com.anushika.typeahead.service;

import org.springframework.stereotype.Service;

/**
 * Centralises the scoring logic for typeahead suggestions.
 *
 * <p>The ranking score is used by both the read path (Cache Aside fallback) and
 * the write path (Cache Refresh batch flushes). By keeping the formula here,
 * we ensure that both paths consistently evaluate query importance, and any
 * changes to the algorithm only need to be made in one place.
 */
@Service
public class RankingService {

    /**
     * Recomputes the ranking score from DB values.
     *
     * <p>Ranking formula: Logarithmic scaling prevents high historical counts
     * from permanently dominating trending queries.
     * <pre>
     * score = log(total_count + 1) + log(trend_score + 1)
     * </pre>
     *
     * @param totalCount current total_count from PostgreSQL
     * @param trendScore current trend_score from PostgreSQL
     * @return rounded score (2 decimal places)
     */
    public double computeScore(long totalCount, double trendScore) {
        double score = Math.log(totalCount + 1) + Math.log(trendScore + 1);
        return Math.round(score * 100.0) / 100.0;
    }
}
