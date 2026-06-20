package com.anushika.typeahead.dto;

/**
 * Response DTO for a single suggestion entry.
 *
 * score is computed at the service layer as:
 *   Math.log(totalCount + 1) + Math.log(trendScore + 1)
 *
 * It is intentionally NOT stored in PostgreSQL to avoid consistency
 * issues across decay cycles.
 */
public record SuggestionResponse(String query, Double score) {}
