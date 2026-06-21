package com.anushika.typeahead.service;

import com.anushika.typeahead.repository.SearchQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a trend-score decay pass to PostgreSQL.
 *
 * <h2>Formula</h2>
 * <pre>
 * UPDATE search_queries
 * SET   trend_score   = trend_score * 0.9,
 *       last_decay_at = NOW()
 * WHERE last_decay_at < NOW() - INTERVAL '20 hours'
 * </pre>
 *
 * <h2>Purpose</h2>
 * Prevents old trending queries from remaining permanently boosted.
 * {@code total_count} accumulates forever (historical popularity), while
 * {@code trend_score} shrinks by 10 % per decay cycle — recent searches
 * gradually lose influence in the ranking formula:
 * <pre>
 * score = log(total_count + 1) + log(trend_score + 1)
 * </pre>
 *
 * <h2>Double-decay guard</h2>
 * The {@code WHERE last_decay_at < NOW() - INTERVAL '20 hours'} predicate
 * ensures that even if the scheduler fires twice within a cycle (e.g. on
 * application restart), each row is decayed at most once per 20-hour window.
 *
 * <p>Called exclusively by {@code TrendDecayScheduler}.
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

    // ── Stats record ──────────────────────────────────────────────────────────

    /**
     * Immutable result of a decay pass.
     *
     * @param rowsUpdated number of rows whose {@code trend_score} was multiplied by 0.9
     * @param durationMs  elapsed wall-clock time in milliseconds for the DB transaction
     */
    public record DecayStats(int rowsUpdated, long durationMs) {}
}
