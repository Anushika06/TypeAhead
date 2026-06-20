package com.anushika.typeahead.repository;

import com.anushika.typeahead.entity.SearchQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchQueryRepository extends JpaRepository<SearchQuery, String> {
    // JpaRepository provides:
    //   count()        → used by /health/db
    //   findAll()      → general access
    //   findById()     → lookup by query string (PK)
    //   save()         → upsert a single row
    //   saveAll()      → batch upsert (used by batch flush worker)
    //
    // Custom query methods will be added here as the suggestion
    // and batch-write features are implemented.
}
