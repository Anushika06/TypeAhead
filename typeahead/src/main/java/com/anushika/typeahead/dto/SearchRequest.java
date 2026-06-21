package com.anushika.typeahead.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for POST /search.
 */
@Schema(description = "Request body for submitting a search event")
public record SearchRequest(

        @Schema(description = "The search query typed by the user", example = "google")
        @NotBlank(message = "query must not be blank")
        @Size(max = 200, message = "query must not exceed 200 characters")
        String query
) {}
