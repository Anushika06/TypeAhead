package com.anushika.typeahead.repository;

import com.anushika.typeahead.entity.SearchQuery;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
