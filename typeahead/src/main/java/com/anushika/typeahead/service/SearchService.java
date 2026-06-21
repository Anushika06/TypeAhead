package com.anushika.typeahead.service;

import com.anushika.typeahead.stream.SearchEventProducer;
import org.springframework.stereotype.Service;

/**
 * Application service for the write path: recording a user search.
 */
@Service
public class SearchService {

    private final SearchEventProducer eventProducer;

    public SearchService(SearchEventProducer eventProducer) {
        this.eventProducer = eventProducer;
    }

    
    public void recordSearch(String rawQuery) {
        String normalised = rawQuery == null ? "" : rawQuery.trim().toLowerCase();

        if (normalised.isBlank()) {
            return;
        }

        eventProducer.publish(normalised);
    }
}
