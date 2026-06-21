package com.anushika.typeahead.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central metrics registry for the Search Typeahead System.
 *
 * <h2>Design</h2>
 * All counters are {@link AtomicLong} — lock-free, safe for concurrent
 * request threads and the scheduler thread without any synchronisation overhead.
 * Values reset on application restart (intentional — these are process-lifetime
 * operational metrics for observability and assignment demonstration).
 *
 * <h2>Counters tracked</h2>
 * <ul>
 *   <li>{@code cacheHits}            — GET /suggest served from Redis</li>
 *   <li>{@code cacheMisses}          — GET /suggest fell back to PostgreSQL</li>
 *   <li>{@code dbReads}              — PostgreSQL SELECT executions (suggest fallbacks)</li>
 *   <li>{@code dbWrites}             — PostgreSQL UPSERT executions (batch flush rows)</li>
 *   <li>{@code streamEventsPublished}— POST /search events pushed to Redis Stream</li>
 *   <li>{@code streamEventsConsumed} — Stream events read and aggregated by consumer</li>
 *   <li>{@code batchFlushCount}      — Number of aggregation flushes executed</li>
 *   <li>{@code totalFlushedEvents}   — Sum of all batch sizes (used to compute avg)</li>
 * </ul>
 *
 * <p>The {@code avgFlushSize} metric is derived at read-time:
 * {@code totalFlushedEvents / batchFlushCount} (returns 0 if no flushes yet).
 *
 * <h2>Exposed via</h2>
 * {@code GET /metrics} — served by {@code CacheDebugController}.
 */
@Service
public class MetricsService {

    // ── Cache counters ────────────────────────────────────────────────────────
    private final AtomicLong cacheHits   = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    // ── Database counters ─────────────────────────────────────────────────────
    private final AtomicLong dbReads  = new AtomicLong(0);
    private final AtomicLong dbWrites = new AtomicLong(0);

    // ── Stream counters ───────────────────────────────────────────────────────
    private final AtomicLong streamEventsPublished = new AtomicLong(0);
    private final AtomicLong streamEventsConsumed  = new AtomicLong(0);

    // ── Batch flush counters ──────────────────────────────────────────────────
    private final AtomicLong batchFlushCount    = new AtomicLong(0);
    private final AtomicLong totalFlushedEvents = new AtomicLong(0);

    // ── Record methods ────────────────────────────────────────────────────────

    /** Called when GET /suggest is served from Redis (cache hit). */
    public void recordCacheHit()  { cacheHits.incrementAndGet(); }

    /** Called when GET /suggest falls back to PostgreSQL (cache miss). */
    public void recordCacheMiss() { cacheMisses.incrementAndGet(); }

    /** Called each time a PostgreSQL SELECT is executed on the suggest fallback path. */
    public void recordDbRead()    { dbReads.incrementAndGet(); }

    /**
     * Called after a batch flush persists rows to PostgreSQL.
     *
     * @param rowCount number of UPSERT statements executed in the flush
     */
    public void recordDbWrites(int rowCount) { dbWrites.addAndGet(rowCount); }

    /** Called each time POST /search successfully publishes an event to Redis Stream. */
    public void recordEventPublished() { streamEventsPublished.incrementAndGet(); }

    /**
     * Called for each stream record consumed and aggregated by the consumer.
     *
     * @param count number of records consumed in this poll
     */
    public void recordEventsConsumed(int count) { streamEventsConsumed.addAndGet(count); }

    /**
     * Called once per aggregation flush, recording both the flush count
     * and the total number of events in the batch.
     *
     * @param batchSize total number of events in the flushed batch
     */
    public void recordBatchFlush(long batchSize) {
        batchFlushCount.incrementAndGet();
        totalFlushedEvents.addAndGet(batchSize);
    }

    // ── Read-only accessors (used by CacheMetrics delegate) ──────────────────

    public long getCacheHits()   { return cacheHits.get(); }
    public long getCacheMisses() { return cacheMisses.get(); }

    // ── Snapshot ──────────────────────────────────────────────────────────────

    /**
     * Returns a consistent snapshot of all current metric values, ordered
     * for predictable JSON serialisation.
     *
     * <p>{@code cacheHitRate} is computed as:
     * {@code hits / (hits + misses) * 100}, rounded to 1 decimal place.
     * Returns {@code 0.0} when no cache requests have been recorded yet.
     *
     * <p>{@code avgFlushSize} is computed as:
     * {@code totalFlushedEvents / batchFlushCount}.
     * Returns {@code 0} when no flushes have occurred yet.
     *
     * @return ordered map of metric name → value, ready for JSON serialisation
     */
    public Map<String, Object> getSnapshot() {
        long hits    = cacheHits.get();
        long misses  = cacheMisses.get();
        long total   = hits + misses;
        long flushes = batchFlushCount.get();
        long flushed = totalFlushedEvents.get();

        double hitRate    = total > 0   ? Math.round((hits * 1000.0 / total)) / 10.0 : 0.0;
        long   avgFlush   = flushes > 0 ? flushed / flushes                           : 0L;

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("cacheHits",             hits);
        snapshot.put("cacheMisses",           misses);
        snapshot.put("cacheHitRate",          hitRate);
        snapshot.put("dbReads",               dbReads.get());
        snapshot.put("dbWrites",              dbWrites.get());
        snapshot.put("streamEventsPublished", streamEventsPublished.get());
        snapshot.put("streamEventsConsumed",  streamEventsConsumed.get());
        snapshot.put("batchFlushCount",       flushes);
        snapshot.put("avgFlushSize",          avgFlush);
        return snapshot;
    }
}
