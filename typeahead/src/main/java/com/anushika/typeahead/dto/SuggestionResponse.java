package com.anushika.typeahead.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for a single suggestion entry.
 *
 * score is computed at the service layer as:
 *   Math.log(totalCount + 1) + Math.log(trendScore + 1)
 *
 * It is intentionally NOT stored in PostgreSQL to avoid consistency
 * issues across decay cycles.
 */
@Schema(description = "Response DTO for a single suggestion entry")
public record SuggestionResponse(
        @Schema(description = "The autocomplete suggestion", example = "google") String query,
        @Schema(description = "Calculated ranking score", example = "19.58") Double score
) {}
