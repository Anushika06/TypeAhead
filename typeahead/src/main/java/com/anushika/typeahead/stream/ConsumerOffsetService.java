package com.anushika.typeahead.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Durable offset store for the Redis Stream consumer.
 *
 * <h2>Why this exists</h2>
 * Without persisting the last successfully processed stream ID across JVM
 * restarts, the consumer always starts from {@code 0-0} and re-reads the
 * entire stream history, re-running aggregation and UPSERT against PostgreSQL
 * for every event that was ever published. This inflates {@code total_count}
 * and {@code trend_score} with every restart.
 *
 * <h2>Storage</h2>
 * The offset is stored as a plain Redis String under the key
 * {@code consumer:lastProcessedId}. Redis is already in the stack and survives
 * application restarts, making it a lightweight, dependency-free choice for
 * this purpose.
 *
 * <h2>Offset semantics</h2>
 * The offset is saved <em>only after a complete, successful flush cycle</em>:
 * <ol>
 *   <li>Events consumed from stream.</li>
 *   <li>Aggregated in {@code ConcurrentHashMap}.</li>
 *   <li>Persisted to PostgreSQL via {@code BatchPersistenceService}.</li>
 *   <li>Cache refreshed via {@code CacheRefreshService}.</li>
 *   <li><b>THEN</b> the offset is advanced.</li>
 * </ol>
 * If the application crashes at any point before step 5, the saved offset is
 * unchanged and the events are re-processed after restart — avoiding data loss
 * at the cost of at-most-once duplication (mitigated by UPSERT semantics).
 *
 * <p>Called exclusively by {@link SearchEventConsumer}.
 */
@Service
public class ConsumerOffsetService {

    private static final Logger log = LoggerFactory.getLogger(ConsumerOffsetService.class);

    /** Redis key where the durable consumer offset is stored. */
    static final String OFFSET_KEY = "consumer:lastProcessedId";

    /** Sentinel value used when no saved offset exists. */
    static final String INITIAL_OFFSET = "0-0";

    private final StringRedisTemplate redisTemplate;

    public ConsumerOffsetService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Reads the last successfully persisted stream ID from Redis.
     *
     * @return the saved offset, or {@code "0-0"} if none has been saved yet
     */
    public String getLastProcessedId() {
        String saved = redisTemplate.opsForValue().get(OFFSET_KEY);
        if (saved == null || saved.isBlank()) {
            log.info("NO OFFSET FOUND. STARTING FROM {}", INITIAL_OFFSET);
            return INITIAL_OFFSET;
        }
        log.info("CONSUMER RESUMED FROM OFFSET {}", saved);
        return saved;
    }

    /**
     * Persists the given stream ID as the latest durable consumer offset.
     *
     * <p>Must only be called <em>after</em> a successful batch flush has been
     * committed to PostgreSQL and the Redis cache has been refreshed.
     *
     * @param streamId the Redis Stream record ID of the last event included
     *                 in the successfully committed batch
     */
    public void saveLastProcessedId(String streamId) {
        redisTemplate.opsForValue().set(OFFSET_KEY, streamId);
        log.info("OFFSET SAVED {}", streamId);
    }
}
