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
     * @param query          the normalised search term
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
     * Applies a single decay pass to all rows.
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

    /**
     * Returns the top 5 queries ranked by the logarithmic scoring formula.
     *
     * @return up to 5 rows ordered highest-score-first
     */
    @Query(value = """
            SELECT *
            FROM search_queries
            ORDER BY (LN(total_count + 1) + LN(trend_score + 1)) DESC
            LIMIT 5
            """, nativeQuery = true)
    List<SearchQuery> findTopTrending();
}
