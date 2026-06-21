-- Search Typeahead System — PostgreSQL schema bootstrap
-- This script runs once when the PostgreSQL container is first initialized.
-- JPA is configured with ddl-auto: validate, so the schema must exist before Spring Boot starts.

CREATE TABLE IF NOT EXISTS search_queries (
    query         VARCHAR(255) NOT NULL,
    total_count   BIGINT       NOT NULL DEFAULT 0,
    trend_score   DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    last_decay_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_search_queries PRIMARY KEY (query)
);

-- Index for prefix-based range queries (LIKE 'abc%' or similar)
CREATE INDEX IF NOT EXISTS idx_search_queries_query ON search_queries (query);

-- Index for ranking queries (ORDER BY ranking score DESC)
CREATE INDEX IF NOT EXISTS idx_search_queries_trend_score ON search_queries (trend_score DESC);
