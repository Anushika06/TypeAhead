package com.anushika.typeahead.service;

import org.springframework.stereotype.Service;

/**
 * Centralises the scoring logic for typeahead suggestions.
 */
@Service
public class RankingService {

    /**
     * Recomputes the ranking score from DB values.
     *
     * @param totalCount current total count
     * @param trendScore current trend score
     * @return rounded score
     */
    public double computeScore(long totalCount, double trendScore) {
        double score = Math.log(totalCount + 1) + Math.log(trendScore + 1);
        return Math.round(score * 100.0) / 100.0;
    }
}
