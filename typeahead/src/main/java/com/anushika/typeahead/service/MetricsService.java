package com.anushika.typeahead.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central metrics registry for the Search Typeahead System.
 */
@Service
public class MetricsService {


    private final AtomicLong cacheHits   = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);


    private final AtomicLong dbReads  = new AtomicLong(0);
    private final AtomicLong dbWrites = new AtomicLong(0);


    private final AtomicLong streamEventsPublished = new AtomicLong(0);
    private final AtomicLong streamEventsConsumed  = new AtomicLong(0);


    private final AtomicLong batchFlushCount    = new AtomicLong(0);
    private final AtomicLong totalFlushedEvents = new AtomicLong(0);



    /** Called when GET /suggest is served from Redis (cache hit). */
    public void recordCacheHit()  { cacheHits.incrementAndGet(); }

    /** Called when GET /suggest falls back to PostgreSQL (cache miss). */
    public void recordCacheMiss() { cacheMisses.incrementAndGet(); }

    /** Called each time a PostgreSQL SELECT is executed on the suggest fallback path. */
    public void recordDbRead()    { dbReads.incrementAndGet(); }

 

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



    public long getCacheHits()   { return cacheHits.get(); }
    public long getCacheMisses() { return cacheMisses.get(); }



    /**
     * Returns a consistent snapshot of all current metric values.
     *
     * @return ordered map of metric name → value
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
