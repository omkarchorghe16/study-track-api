package com.studytrack.api.explanation;

import com.studytrack.api.explanation.ExplanationDtos.PrefillStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/catalog/explanations")
@Tag(name = "Catalog explanations (admin)", description = "One-off pre-fill of the catalog questions' AI explanations")
public class CatalogExplanationController {
    private final CatalogExplanationPrefillService prefillService;

    public CatalogExplanationController(CatalogExplanationPrefillService prefillService) {
        this.prefillService = prefillService;
    }

    @PostMapping("/prefill")
    @Operation(summary = "Write the missing explanations of the catalog's questions with the AI",
            description = "Admin only. Runs in the background, one question at a time and paced for a free API key; only "
                    + "questions without a saved explanation are written, so it can be started again after a stop. Returns "
                    + "at once; follow it with GET /admin/catalog/explanations/status.")
    @ApiResponse(responseCode = "202", description = "Started, or already running")
    @ApiResponse(responseCode = "403", description = "The current user is not an admin")
    public ResponseEntity<PrefillStatusResponse> startPrefill() {
        return ResponseEntity.accepted().body(prefillService.startPrefill());
    }

    @GetMapping("/status")
    @Operation(summary = "Progress of the catalog pre-fill",
            description = "Admin only. state is IDLE, RUNNING, COMPLETED or FAILED (stopped: see message); missing counts "
                    + "catalog questions without a saved explanation, questions the number of catalog questions.")
    @ApiResponse(responseCode = "403", description = "The current user is not an admin")
    public PrefillStatusResponse getPrefillStatus() {
        return prefillService.getPrefillStatus();
    }
}
