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
 * Redis Stream consumer for the {@code search_events} stream.
 *
 * <h2>Responsibilities</h2>
 * <ol>
 *   <li>Poll {@code search_events} every second using {@code XREAD}.</li>
 *   <li>Aggregate duplicate queries in-memory:
 *       {@code Map<query, incrementCount>}.</li>
 *   <li>Flush the aggregation map when either condition is met:
 *       <ul>
 *         <li>1,000 events have been processed since the last flush, <b>or</b></li>
 *         <li>30 seconds have elapsed since the last flush.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h2>What flush does NOT do (yet)</h2>
 * <p>All phases are now active: DB persistence and cache refresh both run
 * after every flush.
 *
 * <h2>Thread safety</h2>
 * Spring's default {@code ThreadPoolTaskScheduler} uses a single-threaded
 * executor for {@code @Scheduled} methods, so the poll loop and all shared
 * state (aggregation map, counters, lastReadId) are accessed by one thread
 * at a time.  {@link ConcurrentHashMap} and {@link AtomicLong} are used
 * defensively in case the scheduler configuration is changed later.
 *
 * <h2>Stream cursor</h2>
 * On startup the consumer begins reading from {@code 0-0} (the very beginning
 * of the stream) so any events already present — e.g. published during a
 * previous run — are included in the first flush.  In production you would
 * persist the last-read ID across restarts; that is deferred to a later phase.
 */
@Component
public class SearchEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SearchEventConsumer.class);

    // ── Configuration constants ───────────────────────────────────────────────

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

    // ── State ────────────────────────────────────────────────────────────────

    private final StringRedisTemplate redisTemplate;
    private final BatchPersistenceService batchPersistenceService;
    private final CacheRefreshService cacheRefreshService;
    private final MetricsService metricsService;
    private final ConsumerOffsetService offsetService;

    /**
     * In-memory aggregation map: query → total count increment since last flush.
     *
     * <p>Using {@link ConcurrentHashMap} for defensive thread safety.
     * {@link ConcurrentHashMap#merge} is atomic and avoids lost-update bugs.
     */
    private final ConcurrentHashMap<String, Long> aggregationMap = new ConcurrentHashMap<>();

    /**
     * Total number of stream events processed since the last flush.
     * Checked against {@link #FLUSH_THRESHOLD_EVENTS}.
     */
    private final AtomicLong pendingEventCount = new AtomicLong(0);

    /**
     * The Redis Stream record ID of the last event consumed.
     * Passed to the next {@code XREAD} call so we only read new events.
     *
     * <p>Initialised from the durable Redis offset in {@link #init()} so that
     * restarts resume from the last successfully committed position rather
     * than replaying the entire stream history from {@code 0-0}.
     */
    private volatile String lastReadId;

    /**
     * The highest stream record ID that has been included in the current
     * (not-yet-flushed) aggregation window.
     *
     * <p>This is the value written to Redis after a successful flush so that
     * the <em>next</em> restart begins exactly after the last committed event.
     * It is updated inside {@link #aggregateRecords} and consumed inside
     * {@link #flush}.
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
     *
     * <p>This runs after dependency injection but before the first
     * {@link #pollAndAggregate()} tick, ensuring the scheduler always starts
     * from the correct position in the stream.
     */
    @PostConstruct
    public void init() {
        String saved = offsetService.getLastProcessedId();
        lastReadId        = saved;
        latestAggregatedId = saved;
    }

    // ── Poll loop ─────────────────────────────────────────────────────────────

    /**
     * Polls the stream every second, aggregates new events, and checks both
     * flush conditions.
     *
     * <p>A {@code fixedDelay} (not {@code fixedRate}) is used intentionally:
     * the next poll starts 1 second <em>after</em> the previous one completes,
     * preventing poll bursts if a poll takes longer than 1 second.
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

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Issues {@code XREAD COUNT <MAX> STREAMS search_events <lastReadId>}
     * and updates {@link #lastReadId} to the last record received.
     *
     * @return list of new records (possibly empty)
     */
    @SuppressWarnings("unchecked")
    private List<MapRecord<String, Object, Object>> readNewRecords() {
        // StreamReadOptions carries COUNT; StreamOffset carries the stream name + cursor ID
        List<MapRecord<String, Object, Object>> records =
                (List<MapRecord<String, Object, Object>>) (List<?>)
                redisTemplate.opsForStream().read(
                        StreamReadOptions.empty().count(MAX_READ_PER_POLL),
                        StreamOffset.create(STREAM_NAME, ReadOffset.from(lastReadId))
                );

        if (records == null || records.isEmpty()) {
            return List.of();
        }

        // Advance the cursor to the last record we just read
        lastReadId = records.get(records.size() - 1).getId().getValue();
        return records;
    }

    /**
     * Accumulates each record's query into the aggregation map and advances
     * {@link #latestAggregatedId} to the highest record ID seen.
     *
     * <p>{@code latestAggregatedId} is deliberately updated here — not in
     * {@link #readNewRecords()} — so it always reflects the last event that
     * was actually added to the aggregation window, not merely read from the
     * stream.  The value is written to Redis only after a successful flush,
     * giving us an at-least-once guarantee without skipping unprocessed events.
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

            // Atomically increment the count for this query
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
     *
     * <ul>
     *   <li>Event threshold: {@value #FLUSH_THRESHOLD_EVENTS} events processed</li>
     *   <li>Time threshold:  {@value #FLUSH_THRESHOLD_SECONDS} seconds elapsed</li>
     * </ul>
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
     * Drains the aggregation map, persists to PostgreSQL, refreshes the cache,
     * and — only on full success — advances the durable consumer offset in Redis.
     *
     * <p>The map is swapped atomically by replacing it with a local snapshot,
     * so events arriving during the flush are captured in the next batch.
     *
     * <p>The offset is saved as the final step so that a crash anywhere earlier
     * in the pipeline leaves the offset unchanged; the events will be
     * re-aggregated on the next restart (at-least-once semantics, safe because
     * the UPSERT is idempotent).
     *
     * @param reason human-readable trigger description for the log
     */
    private void flush(String reason) {
        // Capture the watermark of the events about to be flushed.
        // latestAggregatedId is volatile, so we snapshot it before draining.
        String offsetToSave = latestAggregatedId;

        // Snapshot and clear the map atomically by draining into a local copy
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

        // Log each query → count pair in the batch (descending by count)
        batch.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry ->
                        log.info("  BATCH ENTRY  query={}  count=+{}", entry.getKey(), entry.getValue())
                );

        // ── Persist to PostgreSQL ─────────────────────────────────────────────
        BatchPersistenceService.FlushStats stats = batchPersistenceService.persist(batch);

        // ── Refresh Redis cache (runs after DB commit) ─────────────────────────
        cacheRefreshService.refreshAfterFlush(batch.keySet());

        // ── Record flush metrics ───────────────────────────────────────────────
        metricsService.recordBatchFlush(totalEvents);

        log.info("BATCH FLUSH COMPLETED  rowsUpdated/Inserted={} durationMs={}",
                stats.rowsProcessed(), stats.durationMs());

        // ── Persist consumer offset (MUST be last) ────────────────────────────
        // Only reached if DB + cache refresh succeeded. Crashing before this
        // point leaves the offset unchanged so events are safely re-processed.
        if (offsetToSave != null) {
            offsetService.saveLastProcessedId(offsetToSave);
        }
    }
}
