package com.anushika.typeahead.service;

import com.anushika.typeahead.repository.SearchQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a trend-score decay pass to PostgreSQL.
 */
@Service
public class TrendDecayService {

    private final SearchQueryRepository repository;

    public TrendDecayService(SearchQueryRepository repository) {
        this.repository = repository;
    }

    /**
     * Executes the decay UPDATE in a single transaction.
     *
     * @return {@link DecayStats} with row count and elapsed time
     */
    @Transactional
    public DecayStats applyDecay() {
        long startMs = System.currentTimeMillis();

        int rowsUpdated = repository.decayTrendScores();

        long durationMs = System.currentTimeMillis() - startMs;
        return new DecayStats(rowsUpdated, durationMs);
    }



    /**
     * Immutable result of a decay pass.
     *
     * @param rowsUpdated number of rows updated
     * @param durationMs  elapsed wall-clock time in milliseconds
     */
    public record DecayStats(int rowsUpdated, long durationMs) {}
}
