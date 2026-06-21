package com.anushika.typeahead.service;

import com.anushika.typeahead.repository.SearchQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Persists an aggregated batch of search-count increments to PostgreSQL.
 *
 * <h2>Responsibility</h2>
 * Executes one {@code INSERT ... ON CONFLICT DO UPDATE} per distinct query
 * in the batch, wrapping the entire set in a single transaction so a partial
 * failure leaves the database in a consistent state.
 *
 * <h2>UPSERT semantics</h2>
 * <pre>
 * INSERT INTO search_queries (query, total_count, trend_score, ...)
 * VALUES (:query, :countIncrement, :countIncrement, NOW(), NOW())
 * ON CONFLICT (query)
 * DO UPDATE SET
 *     total_count = search_queries.total_count + :countIncrement,
 *     trend_score = search_queries.trend_score + :countIncrement,
 *     updated_at  = NOW()
 * </pre>
 *
 * <h2>What this service does NOT do</h2>
 * <ul>
 *   <li>Does NOT evict Redis cache entries — that is the next phase.</li>
 *   <li>Does NOT determine inserted vs updated rows — PostgreSQL's
 *       {@code ON CONFLICT} clause handles that silently and efficiently.</li>
 * </ul>
 */
@Service
public class BatchPersistenceService {

    private final SearchQueryRepository repository;
    private final MetricsService metricsService;

    public BatchPersistenceService(SearchQueryRepository repository,
                                   MetricsService metricsService) {
        this.repository = repository;
        this.metricsService = metricsService;
    }

    /**
     * Persists every {@code query → countIncrement} entry in the batch to
     * PostgreSQL inside a single transaction.
     *
     * <p>Returns a {@link FlushStats} record containing:
     * <ul>
     *   <li>{@code rowsProcessed} — number of UPSERT statements executed</li>
     *   <li>{@code durationMs}    — wall-clock time of the entire DB round-trip</li>
     * </ul>
     *
     * <p>Because {@code INSERT ... ON CONFLICT DO UPDATE} returns no
     * distinguishable result for insert vs update, exact inserted/updated counts
     * are not available from the driver.  {@code rowsProcessed} captures the
     * total number of distinct queries flushed, which is logged as both
     * "rows inserted/updated" for observability.
     *
     * @param batch map of normalised query → accumulated count from the aggregation window
     * @return flush statistics
     */
    @Transactional
    public FlushStats persist(Map<String, Long> batch) {
        long startMs = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : batch.entrySet()) {
            repository.upsertQuery(entry.getKey(), entry.getValue());
        }

        long durationMs = System.currentTimeMillis() - startMs;
        metricsService.recordDbWrites(batch.size());
        return new FlushStats(batch.size(), durationMs);
    }

    // ── Stats record ──────────────────────────────────────────────────────────

    /**
     * Immutable result object returned by {@link #persist(Map)}.
     *
     * @param rowsProcessed number of UPSERT statements executed (= distinct queries in batch)
     * @param durationMs    elapsed wall-clock time in milliseconds for the entire DB transaction
     */
    public record FlushStats(int rowsProcessed, long durationMs) {}
}
