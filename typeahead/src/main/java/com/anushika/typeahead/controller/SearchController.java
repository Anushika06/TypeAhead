package com.anushika.typeahead.controller;

import com.anushika.typeahead.dto.SearchRequest;
import com.anushika.typeahead.service.SearchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

/**
 * Write-path controller: records user search events.
 */
@Tag(name = "Search Events", description = "Asynchronous search submission endpoints")
@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Records a search query by publishing it to the Redis Stream.
     *
     * @param request validated request body containing the raw query string.
     * @return 200 OK with message.
     */
    @Operation(
            summary = "Record a search event",
            description = "Publishes a search event to Redis Streams.\n\nEvents are processed asynchronously through\naggregation and batch persistence.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation")
            }
    )
    @PostMapping("/search")
    public ResponseEntity<Map<String, String>> search(
            @Valid @RequestBody SearchRequest request) {

        searchService.recordSearch(request.query());
        return ResponseEntity.ok(Map.of("message", "Searched"));
    }
}
