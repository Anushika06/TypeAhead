package com.anushika.typeahead.repository;

import com.anushika.typeahead.entity.SearchQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchQueryRepository extends JpaRepository<SearchQuery, String> {

    // JpaRepository provides:
    //   count()     → used by /health/db
    //   save()      → upsert a single row
    //   saveAll()   → batch upsert (used by batch flush worker)

    /**
     * Fetches up to 100 candidate rows whose query starts with the given prefix
     * (case-insensitive). Pre-sorted by (total_count + trend_score) DESC as a
     * cheap approximation — the service layer will re-rank using the precise
     * log-score formula: log(total_count+1) + log(trend_score+1).
     *
     * 100 candidates gives enough headroom so that no true top-10 result is
     * missed by the pre-filter, even when trend_score diverges from total_count.
     */
    @Query(value = """
            SELECT *
            FROM search_queries
            WHERE LOWER(query) LIKE LOWER(:prefix) || '%'
            ORDER BY (total_count + trend_score) DESC
            LIMIT 100
            """, nativeQuery = true)
    List<SearchQuery> findCandidatesByPrefix(@Param("prefix") String prefix);

    /**
     * Atomic UPSERT for a single query + count increment.
     *
     * <p>On INSERT: creates a new row with both {@code total_count} and
     * {@code trend_score} set to {@code countIncrement}, and timestamps
     * {@code last_decay_at} and {@code updated_at} to NOW().
     *
     * <p>On UPDATE (conflict on {@code query}): increments both
     * {@code total_count} and {@code trend_score} by {@code countIncrement}
     * and refreshes {@code updated_at} to NOW().  {@code last_decay_at} is
     * intentionally NOT updated — the decay scheduler owns that column.
     *
     * <p>Must be called inside a transaction (handled by
     * {@code BatchPersistenceService}).
     *
     * @param query          the normalised search term (lowercase, trimmed)
     * @param countIncrement number of times this query was searched in the batch
     */
    @Modifying
    @Query(value = """
            INSERT INTO search_queries (query, total_count, trend_score, last_decay_at, updated_at)
            VALUES (:query, :countIncrement, :countIncrement, NOW(), NOW())
            ON CONFLICT (query)
            DO UPDATE SET
                total_count = search_queries.total_count + :countIncrement,
                trend_score = search_queries.trend_score + :countIncrement,
                updated_at  = NOW()
            """, nativeQuery = true)
    void upsertQuery(@Param("query") String query,
                     @Param("countIncrement") long countIncrement);

    /**
     * Applies a single decay pass to all rows whose {@code last_decay_at}
     * timestamp is older than 20 hours.
     *
     * <p>The 20-hour guard prevents accidental double-decay if the scheduler
     * fires more than once within a decay window (e.g. during a restart).
     *
     * <p>Formula: {@code trend_score = trend_score * 0.9}
     *
     * <p>Must be called inside a transaction (handled by
     * {@code TrendDecayService}).
     *
     * @return number of rows whose trend_score was updated
     */
    @Modifying
    @Query(value = """
            UPDATE search_queries
            SET trend_score   = trend_score * 0.9,
                last_decay_at = NOW()
            WHERE last_decay_at < NOW() - INTERVAL '20 hours'
            """, nativeQuery = true)
    int decayTrendScores();
}
