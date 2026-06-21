package com.anushika.typeahead.service;

import com.anushika.typeahead.repository.SearchQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Persists an aggregated batch of search-count increments to PostgreSQL.
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



    
    public record FlushStats(int rowsProcessed, long durationMs) {}
}
