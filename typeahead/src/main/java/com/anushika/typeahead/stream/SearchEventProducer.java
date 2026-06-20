package com.anushika.typeahead.stream;

import com.anushika.typeahead.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes search query events to the Redis Stream {@value #STREAM_NAME}.
 *
 * <h2>Redis Streams — why?</h2>
 * <ul>
 *   <li>Fire-and-forget for the request thread: {@code XADD} is O(1) and
 *       returns immediately with the auto-generated record ID.</li>
 *   <li>Durable log: events are persisted in Redis until explicitly trimmed
 *       or acknowledged, unlike Pub/Sub which drops messages for
 *       offline consumers.</li>
 *   <li>Consumer groups (added later) allow multiple workers to share
 *       the batch-update load without duplicate processing.</li>
 * </ul>
 *
 * <h2>Event payload</h2>
 * <pre>
 * Stream:  search_events
 * Fields:
 *   query  →  normalised search term   e.g. "google"
 * </pre>
 *
 * <p>The producer does NOT update PostgreSQL or invalidate any cache.
 * Those responsibilities belong to the consumer (implemented later).
 */
@Component
public class SearchEventProducer {

    private static final Logger log = LoggerFactory.getLogger(SearchEventProducer.class);

    /** Redis Stream name for search events. */
    public static final String STREAM_NAME = "search_events";

    /** Field key for the search query inside each stream record. */
    private static final String FIELD_QUERY = "query";

    private final StringRedisTemplate redisTemplate;
    private final MetricsService metricsService;

    public SearchEventProducer(StringRedisTemplate redisTemplate,
                               MetricsService metricsService) {
        this.redisTemplate = redisTemplate;
        this.metricsService = metricsService;
    }

    /**
     * Publishes a single search event into the {@value #STREAM_NAME} stream.
     *
     * <p>Internally issues {@code XADD search_events * query <normalised>}.
     * The {@code *} wildcard lets Redis auto-generate a monotonic record ID
     * (millisecond timestamp + sequence).
     *
     * <p>On any Redis error the exception is logged and swallowed — a failed
     * stream publish must not degrade the user-facing response.
     *
     * @param normalisedQuery lowercase, trimmed search term
     */
    public void publish(String normalisedQuery) {
        try {
            // XADD search_events * query <normalisedQuery>
            RecordId recordId = redisTemplate
                    .opsForStream()
                    .add(STREAM_NAME, Map.of(FIELD_QUERY, normalisedQuery));

            log.info("SEARCH EVENT PUBLISHED  query={}  id={}", normalisedQuery, recordId);
            metricsService.recordEventPublished();
        } catch (Exception ex) {
            log.error("Failed to publish search event for query='{}': {}",
                    normalisedQuery, ex.getMessage());
        }
    }
}
