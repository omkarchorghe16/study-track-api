package com.studytrack.api.search;

import com.studytrack.api.search.SearchDtos.SearchPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/items/search")
@Tag(name = "Search", description = "Full-text search across the current user's items")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @Operation(summary = "Search items",
            description = "Items whose title, question, answer or notes contain every word of q (words match as prefixes), "
                    + "best match first. The snippet marks the matching words.")
    public SearchPageResponse searchItems(
            @Parameter(description = "Words to find") @RequestParam @NotBlank @Size(max = 200) String q,
            @Parameter(description = "Only items of this subject") @RequestParam(required = false) Long subjectId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return searchService.searchItems(q, subjectId, page, size);
    }
}
