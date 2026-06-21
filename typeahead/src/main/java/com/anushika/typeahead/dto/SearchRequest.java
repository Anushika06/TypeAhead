package com.anushika.typeahead.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for {@code POST /search}.
 *
 * <p>The {@code query} field is the raw search term as typed by the user.
 * Normalisation (trim + lowercase) happens in {@code SearchService}, not here,
 * so that validation messages can reference the original input.
 *
 * <p>Constraints:
 * <ul>
 *   <li>Must not be blank (null or whitespace-only is rejected).</li>
 *   <li>Limited to 200 characters to prevent oversized stream payloads.</li>
 * </ul>
 */
@Schema(description = "Request body for submitting a search event")
public record SearchRequest(

        @Schema(description = "The search query typed by the user", example = "google")
        @NotBlank(message = "query must not be blank")
        @Size(max = 200, message = "query must not exceed 200 characters")
        String query
) {}
