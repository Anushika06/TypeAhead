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
     * Publishes a single search event into the stream.
     *
     * @param normalisedQuery lowercase, trimmed search term
     */
    public void publish(String normalisedQuery) {
        try {
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
