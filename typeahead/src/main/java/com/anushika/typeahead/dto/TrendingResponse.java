package com.anushika.typeahead.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for a single trending query entry.
 *
 * <p>Intentionally separate from {@link SuggestionResponse}: trending is a
 * different business concept from autocomplete.  The frontend needs both
 * the ranking score and the raw counts to display meaningful context.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code query}      — normalised search term</li>
 *   <li>{@code score}      — log(total_count + 1) + log(trend_score + 1), rounded to 2 dp</li>
 *   <li>{@code totalCount} — cumulative historical search volume</li>
 *   <li>{@code trendScore} — decaying recency signal (decays 10 % nightly)</li>
 * </ul>
 */
@Schema(description = "Response DTO for a single trending query entry")
public record TrendingResponse(
        @Schema(description = "Normalised search term", example = "google") String query,
        @Schema(description = "Calculated ranking score", example = "19.62") Double score,
        @Schema(description = "Cumulative historical search volume", example = "32532") Long totalCount,
        @Schema(description = "Decaying recency signal", example = "10224.0") Double trendScore
) {}
