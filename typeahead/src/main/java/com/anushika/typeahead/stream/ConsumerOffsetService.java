package com.anushika.typeahead.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Durable offset store for the Redis Stream consumer.
 */
@Service
public class ConsumerOffsetService {

    private static final Logger log = LoggerFactory.getLogger(ConsumerOffsetService.class);

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
     * @return the saved offset
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
     * @param streamId the Redis Stream record ID
     */
    
    public void saveLastProcessedId(String streamId) {
        redisTemplate.opsForValue().set(OFFSET_KEY, streamId);
        log.info("OFFSET SAVED {}", streamId);
    }
}
