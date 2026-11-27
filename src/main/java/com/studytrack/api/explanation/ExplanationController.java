package com.studytrack.api.explanation;

import com.studytrack.api.explanation.ExplanationDtos.ExplanationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items/{questionId}/explanation")
@Tag(name = "Explanations", description = "AI-written detailed explanations of questions, shared by all users")
public class ExplanationController {
    private final ExplanationService explanationService;

    public ExplanationController(ExplanationService explanationService) {
        this.explanationService = explanationService;
    }

    @GetMapping
    @Operation(summary = "Get the saved explanation of a question", description = "Never calls the AI.")
    @ApiResponse(responseCode = "404", description = "No such question, or no explanation is saved yet")
    public ExplanationResponse getExplanation(@PathVariable Long questionId) {
        return explanationService.getExplanation(questionId);
    }

    @PostMapping
    @Operation(summary = "Get the explanation of a question, writing it with the AI if none is saved",
            description = "Returns the saved explanation. Only when there is none, the AI (the provider and model of the "
                    + "Settings page) writes one, which can take up to a minute; it is saved and shared by all users, so "
                    + "the AI is called once per question. The question's own answer is never changed.")
    @ApiResponse(responseCode = "404", description = "No such question")
    @ApiResponse(responseCode = "409", description = "AI is turned off, or the provider's API key is not set on the server")
    @ApiResponse(responseCode = "424", description = "The provider failed or gave no usable explanation")
    public ExplanationResponse findOrCreateExplanation(@PathVariable Long questionId) {
        return explanationService.findOrCreateExplanation(questionId);
    }

    @DeleteMapping
    @Operation(summary = "Remove the saved explanation of a question (admin only)",
            description = "The next POST writes a new one with the AI.")
    @ApiResponse(responseCode = "204", description = "Removed (also when none was saved)")
    @ApiResponse(responseCode = "403", description = "The current user is not an admin")
    @ApiResponse(responseCode = "404", description = "No such question")
    public ResponseEntity<Void> deleteExplanation(@PathVariable Long questionId) {
        explanationService.deleteExplanation(questionId);
        return ResponseEntity.noContent().build();
    }
}
