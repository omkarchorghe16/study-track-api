package com.studytrack.api.explanation;

import com.studytrack.api.explanation.ExplanationDtos.ExplanationBatchResponse;
import com.studytrack.api.explanation.ExplanationDtos.ExplanationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Explanations", description = "AI-written detailed explanations of questions, next to their own answers")
public class ExplanationController {
    private final ExplanationService explanationService;
    private final ExplanationBatchService explanationBatchService;

    public ExplanationController(ExplanationService explanationService,
                                 ExplanationBatchService explanationBatchService) {
        this.explanationService = explanationService;
        this.explanationBatchService = explanationBatchService;
    }

    @GetMapping("/items/{questionId}/explanation")
    @Operation(summary = "Get the explanation of a question")
    @ApiResponse(responseCode = "404", description = "No such question, or it has no explanation yet")
    public ExplanationResponse getExplanation(@PathVariable Long questionId) {
        return explanationService.getExplanation(questionId);
    }

    @PostMapping("/items/{questionId}/explanation")
    @Operation(summary = "Write (or rewrite) the explanation of a question with the AI",
            description = "Uses the provider and model of the Settings page. Can take up to a minute. The question's own "
                    + "answer is never changed.")
    @ApiResponse(responseCode = "404", description = "No such question")
    @ApiResponse(responseCode = "409", description = "AI is turned off, or the provider's API key is not set on the server")
    @ApiResponse(responseCode = "424", description = "The provider failed or gave no usable explanation")
    public ExplanationResponse generateExplanation(@PathVariable Long questionId) {
        return explanationService.generateExplanation(questionId);
    }

    @PostMapping("/subjects/{subjectId}/explanations")
    @Operation(summary = "Explain all questions of a subject in the background",
            description = "Writes an explanation for every question that has none yet, one after another. Returns at "
                    + "once; follow it with GET /subjects/{id}/explanations/status.")
    @ApiResponse(responseCode = "202", description = "Started, or already running")
    @ApiResponse(responseCode = "404", description = "No such subject")
    public ResponseEntity<ExplanationBatchResponse> startSubjectExplanations(@PathVariable Long subjectId) {
        return ResponseEntity.accepted().body(explanationBatchService.startSubjectExplanations(subjectId));
    }

    @GetMapping("/subjects/{subjectId}/explanations/status")
    @Operation(summary = "Progress of the background run for a subject",
            description = "state is IDLE, RUNNING, COMPLETED or FAILED; missing counts questions still without an explanation.")
    @ApiResponse(responseCode = "404", description = "No such subject")
    public ExplanationBatchResponse getSubjectExplanationStatus(@PathVariable Long subjectId) {
        return explanationBatchService.getSubjectExplanationStatus(subjectId);
    }
}
