package com.anushika.typeahead.dto;

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
public record TrendingResponse(
        String query,
        Double score,
        Long   totalCount,
        Double trendScore
) {}
