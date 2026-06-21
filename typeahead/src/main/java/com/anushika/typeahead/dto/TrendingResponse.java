package com.anushika.typeahead.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for a single trending query entry.
 */
@Schema(description = "Response DTO for a single trending query entry")
public record TrendingResponse(
        @Schema(description = "Normalised search term", example = "google") String query,
        @Schema(description = "Calculated ranking score", example = "19.62") Double score,
        @Schema(description = "Cumulative historical search volume", example = "32532") Long totalCount,
        @Schema(description = "Decaying recency signal", example = "10224.0") Double trendScore
) {}
