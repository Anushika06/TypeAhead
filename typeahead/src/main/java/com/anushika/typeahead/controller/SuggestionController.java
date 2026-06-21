package com.anushika.typeahead.controller;

import com.anushika.typeahead.dto.SuggestionResponse;
import com.anushika.typeahead.service.SuggestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Suggestions", description = "Autocomplete suggestion endpoints")
@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class SuggestionController {

    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    /**
     * GET /suggest?q=<prefix>
     *
     * Returns up to 10 ranked suggestions for the given prefix.
     * Returns an empty list (not 400) when q is absent or shorter than 3 chars —
     * the frontend should simply show nothing rather than receive an error.
     *
     * Examples:
     *   /suggest?q=go     → []            (prefix < 3 chars)
     *   /suggest?q=goo    → top 10 matches
     *   /suggest?q=Google → case-insensitive, same as "google"
     */
    @Operation(
            summary = "Get autocomplete suggestions",
            description = "Returns the top ranked autocomplete suggestions\nfor the provided prefix.\n\nMinimum prefix length: 3 characters.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation")
            }
    )
    @GetMapping("/suggest")
    public ResponseEntity<List<SuggestionResponse>> suggest(
            @Parameter(description = "Prefix to search for", example = "goo")
            @RequestParam(value = "q", required = false, defaultValue = "") String prefix
    ) {
        List<SuggestionResponse> suggestions = suggestionService.getSuggestions(prefix);
        return ResponseEntity.ok(suggestions);
    }
}
