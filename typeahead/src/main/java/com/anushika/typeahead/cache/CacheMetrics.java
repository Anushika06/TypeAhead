package com.anushika.typeahead.cache;

import com.anushika.typeahead.service.MetricsService;
import org.springframework.stereotype.Component;

/**
 * Thin delegate that forwards cache hit/miss signals to MetricsService.
 */
@Component
public class CacheMetrics {

    private final MetricsService metricsService;

    public CacheMetrics(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /** Forwards to {@link MetricsService#recordCacheHit()}. */
    public void recordCacheHit()  { metricsService.recordCacheHit(); }

    /** Forwards to {@link MetricsService#recordCacheMiss()}. */
    public void recordCacheMiss() { metricsService.recordCacheMiss(); }

    /** @return total cache hits (delegated to MetricsService) */
    public long getCacheHits()    { return metricsService.getCacheHits(); }

    /** @return total cache misses (delegated to MetricsService) */
    public long getCacheMisses()  { return metricsService.getCacheMisses(); }

    /** @return hits + misses */
    public long getTotalRequests() {
        return metricsService.getCacheHits() + metricsService.getCacheMisses();
    }
}
