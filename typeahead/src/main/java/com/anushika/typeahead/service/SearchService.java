package com.anushika.typeahead.service;

import com.anushika.typeahead.stream.SearchEventProducer;
import org.springframework.stereotype.Service;

/**
 * Application service for the write path: recording a user search.
 *
 * <h2>Responsibilities</h2>
 * <ol>
 *   <li>Normalise the raw query (trim whitespace + lowercase).</li>
 *   <li>Reject blank queries after normalisation (defensive guard).</li>
 *   <li>Delegate to {@link SearchEventProducer} to publish into Redis Streams.</li>
 * </ol>
 *
 * <h2>What this service does NOT do</h2>
 * <ul>
 *   <li>Does NOT write to PostgreSQL — that is the consumer's job.</li>
 *   <li>Does NOT invalidate the Redis cache — that happens after the
 *       batch flush, also in the consumer.</li>
 * </ul>
 *
 * <p>By keeping the request thread work to normalisation + a single
 * {@code XADD}, {@code POST /search} returns in O(1) regardless of
 * database load.
 */
@Service
public class SearchService {

    private final SearchEventProducer eventProducer;

    public SearchService(SearchEventProducer eventProducer) {
        this.eventProducer = eventProducer;
    }

    /**
     * Records a user search by publishing it to the Redis Stream.
     *
     * @param rawQuery the search term as received from the API request
     */
    public void recordSearch(String rawQuery) {
        // Normalise: match the same convention used by the read path
        String normalised = rawQuery == null ? "" : rawQuery.trim().toLowerCase();

        if (normalised.isBlank()) {
            // Nothing to record — blank queries are not useful events
            return;
        }

        eventProducer.publish(normalised);
    }
}
