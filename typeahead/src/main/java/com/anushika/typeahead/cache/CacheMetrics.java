package com.anushika.typeahead.cache;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-lifetime counters for Redis cache observability.
 *
 * <p>Counters are kept in {@link AtomicLong} fields so they are safe under
 * concurrent request handling without any locking.  They are intentionally
 * not persisted — they reset on application restart, which is acceptable
 * for a debugging / assignment demonstration tool.
 *
 * <p>Usage pattern:
 * <pre>
 *   // in SuggestionService
 *   if (cacheHit) {
 *       cacheMetrics.recordCacheHit();
 *   } else {
 *       cacheMetrics.recordCacheMiss();
 *   }
 * </pre>
 *
 * <p>Metrics are exposed via {@code GET /metrics} (future phase) and
 * surfaced alongside key inspection by {@code GET /cache/debug?prefix=}.
 */
@Component
public class CacheMetrics {

    private final AtomicLong cacheHits   = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    // ──────────────────────────────────────────────────────────────────────────
    // Recording
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Increments the cache-hit counter.
     * Call this every time a prefix is found in Redis.
     */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    /**
     * Increments the cache-miss counter.
     * Call this every time a prefix is NOT found in Redis and the DB is queried.
     */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Reading
    // ──────────────────────────────────────────────────────────────────────────

    /** @return total number of cache hits since the last application restart */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /** @return total number of cache misses since the last application restart */
    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Convenience: total requests routed through the cache-aside path
     * (hits + misses).  Requests that were rejected early (prefix &lt; 3 chars)
     * are NOT counted.
     *
     * @return hits + misses
     */
    public long getTotalRequests() {
        return cacheHits.get() + cacheMisses.get();
    }
}
