package com.anushika.typeahead.stream;

import com.anushika.typeahead.cache.CacheRefreshService;
import com.anushika.typeahead.service.BatchPersistenceService;
import com.anushika.typeahead.service.MetricsService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumes Redis Stream events and aggregates them for batched persistence.
 */
@Component
public class SearchEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SearchEventConsumer.class);



    /** Stream to consume from. Must match {@link SearchEventProducer#STREAM_NAME}. */
    private static final String STREAM_NAME = "search_events";

    /** Field name in each stream record that holds the search query. */
    private static final String FIELD_QUERY = "query";

    /** Maximum number of events to read per poll to avoid blocking the scheduler. */
    private static final long MAX_READ_PER_POLL = 100;

    /** Flush after this many events have accumulated since the last flush. */
    private static final long FLUSH_THRESHOLD_EVENTS = 1_000;

    /** Flush after this many seconds regardless of event count. */
    private static final long FLUSH_THRESHOLD_SECONDS = 30;



    private final StringRedisTemplate redisTemplate;
    private final BatchPersistenceService batchPersistenceService;
    private final CacheRefreshService cacheRefreshService;
    private final MetricsService metricsService;
    private final ConsumerOffsetService offsetService;

    /**
     * In-memory aggregation map: query → total count increment since last flush.
     */
    private final ConcurrentHashMap<String, Long> aggregationMap = new ConcurrentHashMap<>();

    /**
     * Total number of stream events processed since the last flush.
     * Checked against {@link #FLUSH_THRESHOLD_EVENTS}.
     */
    private final AtomicLong pendingEventCount = new AtomicLong(0);

    /**
     * The Redis Stream record ID of the last event consumed.
     */
    private volatile String lastReadId;

    /**
     * The highest stream record ID that has been included in the current aggregation window.
     */
    private volatile String latestAggregatedId;


    /** Timestamp of the most recent flush (or application startup). */
    private volatile Instant lastFlushTime = Instant.now();


    public SearchEventConsumer(StringRedisTemplate redisTemplate,
                               BatchPersistenceService batchPersistenceService,
                               CacheRefreshService cacheRefreshService,
                               MetricsService metricsService,
                               ConsumerOffsetService offsetService) {
        this.redisTemplate = redisTemplate;
        this.batchPersistenceService = batchPersistenceService;
        this.cacheRefreshService = cacheRefreshService;
        this.metricsService = metricsService;
        this.offsetService = offsetService;
    }

    /**
     * Loads the durable consumer offset from Redis on startup.
     */
    
    @PostConstruct
    public void init() {
        String saved = offsetService.getLastProcessedId();
        lastReadId        = saved;
        latestAggregatedId = saved;
    }



    /**
     * Polls the stream every second, aggregates new events, and checks both flush conditions.
     */
    @Scheduled(fixedDelay = 1000)
    public void pollAndAggregate() {
        try {
            List<MapRecord<String, Object, Object>> records = readNewRecords();

            if (!records.isEmpty()) {
                aggregateRecords(records);
            }

            checkAndFlush();

        } catch (Exception ex) {
            // Never let the scheduler die — log and continue
            log.error("Error in stream poll loop: {}", ex.getMessage(), ex);
        }
    }



    /**
     * Issues XREAD command and updates lastReadId to the last record received.
     *
     * @return list of new records
     */
    @SuppressWarnings("unchecked")
    private List<MapRecord<String, Object, Object>> readNewRecords() {
        List<MapRecord<String, Object, Object>> records =
                (List<MapRecord<String, Object, Object>>) (List<?>)
                redisTemplate.opsForStream().read(
                        StreamReadOptions.empty().count(MAX_READ_PER_POLL),
                        StreamOffset.create(STREAM_NAME, ReadOffset.from(lastReadId))
                );

        if (records == null || records.isEmpty()) {
            return List.of();
        }

        lastReadId = records.get(records.size() - 1).getId().getValue();
        return records;
    }

    /**
     * Accumulates each record's query into the aggregation map and advances latestAggregatedId.
     *
     * @param records new stream records to aggregate
     */
    private void aggregateRecords(List<MapRecord<String, Object, Object>> records) {
        int consumed = 0;
        for (MapRecord<String, Object, Object> record : records) {
            Object queryObj = record.getValue().get(FIELD_QUERY);
            if (queryObj == null) {
                continue;
            }

            String query = queryObj.toString();
            if (query.isBlank()) {
                continue;
            }

            aggregationMap.merge(query, 1L, Long::sum);
            pendingEventCount.incrementAndGet();
            consumed++;

            // Track the watermark of the current aggregation window
            latestAggregatedId = record.getId().getValue();
        }
        if (consumed > 0) {
            metricsService.recordEventsConsumed(consumed);
        }
    }

    /**
     * Evaluates both flush conditions and triggers a flush if either is met.
     */
    private void checkAndFlush() {
        long pending = pendingEventCount.get();
        long elapsedSeconds = Instant.now().getEpochSecond() - lastFlushTime.getEpochSecond();

        boolean eventThresholdMet = pending >= FLUSH_THRESHOLD_EVENTS;
        boolean timeThresholdMet  = elapsedSeconds >= FLUSH_THRESHOLD_SECONDS;

        if (pending > 0 && (eventThresholdMet || timeThresholdMet)) {
            String reason = eventThresholdMet ? "event threshold (" + pending + " events)"
                                              : "time threshold (" + elapsedSeconds + "s elapsed)";
            flush(reason);
        }
    }

    /**
     * Drains the aggregation map, persists to PostgreSQL, and refreshes the cache.
     *
     * @param reason human-readable trigger description for the log
     */
    private void flush(String reason) {
        // Capture the watermark of the events about to be flushed.
        // latestAggregatedId is volatile, so we snapshot it before draining.
        String offsetToSave = latestAggregatedId;

        Map<String, Long> batch = new HashMap<>();
        aggregationMap.forEach((query, count) -> {
            Long removed = aggregationMap.remove(query);
            if (removed != null) {
                // accumulate in case a concurrent merge happened between forEach and remove
                batch.merge(query, removed, Long::sum);
            }
        });

        long totalEvents = pendingEventCount.getAndSet(0);
        lastFlushTime = Instant.now();

        if (batch.isEmpty()) {
            return;
        }

        log.info("BATCH FLUSH STARTED  reason={} distinctQueries={} totalEvents={}",
                reason, batch.size(), totalEvents);

        batch.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry ->
                        log.info("  BATCH ENTRY  query={}  count=+{}", entry.getKey(), entry.getValue())
                );

        BatchPersistenceService.FlushStats stats = batchPersistenceService.persist(batch);

        cacheRefreshService.refreshAfterFlush(batch.keySet());

        metricsService.recordBatchFlush(totalEvents);

        log.info("BATCH FLUSH COMPLETED  rowsUpdated/Inserted={} durationMs={}",
                stats.rowsProcessed(), stats.durationMs());

        // Only reached if DB + cache refresh succeeded. Crashing before this
        // point leaves the offset unchanged so events are safely re-processed.
        if (offsetToSave != null) {
            offsetService.saveLastProcessedId(offsetToSave);
        }
    }
}
