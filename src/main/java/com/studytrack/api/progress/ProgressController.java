package com.studytrack.api.progress;

import com.studytrack.api.item.ItemDtos.ItemProgressResponse;
import com.studytrack.api.progress.ProgressDtos.DueItemsResponse;
import com.studytrack.api.progress.ProgressDtos.ResumeResponse;
import com.studytrack.api.progress.ProgressDtos.ReviewRequest;
import com.studytrack.api.progress.ProgressDtos.ReviewResponse;
import com.studytrack.api.progress.ProgressDtos.SaveSessionRequest;
import com.studytrack.api.progress.ProgressDtos.TopicCompletionResponse;
import com.studytrack.api.progress.ProgressDtos.UpdateItemProgressRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "Progress", description = "Item status, topic completion, flashcard reviews and the resume point")
public class ProgressController {
    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @PatchMapping("/items/{itemId}/progress")
    @Operation(summary = "Update an item's progress", description = "Missing fields stay unchanged; confidence 0 clears it.")
    public ItemProgressResponse updateItemProgress(@PathVariable Long itemId,
                                                   @Valid @RequestBody UpdateItemProgressRequest request) {
        return progressService.updateItemProgress(itemId, request);
    }

    @PostMapping("/topics/{topicId}/complete")
    @Operation(summary = "Mark a topic complete")
    public TopicCompletionResponse markTopicComplete(@PathVariable Long topicId) {
        return progressService.markTopicComplete(topicId);
    }

    @DeleteMapping("/topics/{topicId}/complete")
    @Operation(summary = "Unmark a topic complete")
    public TopicCompletionResponse unmarkTopicComplete(@PathVariable Long topicId) {
        return progressService.unmarkTopicComplete(topicId);
    }

    @PostMapping("/reviews")
    @Operation(summary = "Log a flashcard result", description = "The server schedules the next review and may change the item's status.")
    public ReviewResponse recordReview(@Valid @RequestBody ReviewRequest request) {
        return progressService.recordReview(request);
    }

    @GetMapping("/reviews/due")
    @Operation(summary = "Flashcards due today", description = "Overdue and due-today reviews first, then items never reviewed.")
    @ApiResponse(responseCode = "404", description = "No such subject for the current user")
    public DueItemsResponse listDueItems(
            @RequestParam Long subjectId,
            @Parameter(description = "Most cards to return") @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return progressService.listDueItems(subjectId, limit);
    }

    @GetMapping("/sessions/resume")
    @Operation(summary = "Where to continue", description = "For one subject, or the most recently used one when subjectId is missing.")
    @ApiResponse(responseCode = "204", description = "Nothing opened yet")
    public ResponseEntity<ResumeResponse> getResumePoint(@RequestParam(required = false) Long subjectId) {
        return progressService.findResumePoint(subjectId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/sessions")
    @Operation(summary = "Save where the user is", description = "Called when a subject, topic or item is opened.")
    public ResumeResponse saveResumePoint(@Valid @RequestBody SaveSessionRequest request) {
        return progressService.saveResumePoint(request);
    }
}
