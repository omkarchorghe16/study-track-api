package com.studytrack.api.importer;

import com.studytrack.api.importer.ImportDtos.ApproveResponse;
import com.studytrack.api.importer.ImportDtos.CandidateRequest;
import com.studytrack.api.importer.ImportDtos.CandidateResponse;
import com.studytrack.api.importer.ImportDtos.CandidateSelectionRequest;
import com.studytrack.api.importer.ImportDtos.ImportJobResponse;
import com.studytrack.api.importer.ImportDtos.RejectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/imports")
@Validated
@Tag(name = "Imports", description = "Turn Word, PDF, Markdown and text notes into reviewable items with an LLM")
public class ImportController {
    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Start an import",
            description = "Uploads a .docx, .pdf, .md or .txt file (at most 20 MB) for a subject. Processing runs in the background; poll GET /imports/{id}.")
    @ApiResponse(responseCode = "202", description = "Job created; processing has started")
    @ApiResponse(responseCode = "409", description = "Imports are switched off, or the LLM is not configured (for example a missing API key)")
    @ApiResponse(responseCode = "413", description = "File too large")
    @ApiResponse(responseCode = "415", description = "Not a supported document")
    public ResponseEntity<ImportJobResponse> createImport(@RequestPart("file") MultipartFile file,
                                                          @RequestParam Long subjectId) {
        ImportJobResponse job = importService.createImport(file, subjectId);
        return ResponseEntity.accepted().location(URI.create("/api/v1/imports/" + job.id())).body(job);
    }

    @GetMapping
    @Operation(summary = "List recent imports", description = "Newest first.")
    public List<ImportJobResponse> listImports(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return importService.listImports(limit);
    }

    @GetMapping("/{importId}")
    @Operation(summary = "Get an import's status and progress", description = "Poll every 2-3 seconds while the status is UPLOADED, PARSING or EXTRACTING.")
    public ImportJobResponse getImport(@PathVariable Long importId) {
        return importService.getImport(importId);
    }

    @GetMapping("/{importId}/candidates")
    @Operation(summary = "List an import's candidates", description = "Document order, with duplicate flags for open candidates.")
    public List<CandidateResponse> listCandidates(@PathVariable Long importId) {
        return importService.listCandidates(importId);
    }

    @PutMapping("/{importId}/candidates/{candidateId}")
    @Operation(summary = "Edit a candidate", description = "Replaces its content and target topic and marks it EDITED. HTML is sanitised.")
    @ApiResponse(responseCode = "409", description = "Still running, or the candidate was already approved or rejected")
    public CandidateResponse updateCandidate(@PathVariable Long importId, @PathVariable Long candidateId,
                                             @Valid @RequestBody CandidateRequest request) {
        return importService.updateCandidate(importId, candidateId, request);
    }

    @PostMapping("/{importId}/approve")
    @Operation(summary = "Approve candidates", description = "Creates missing topics and the items, and links their images. Already approved or rejected candidates are skipped.")
    public ApproveResponse approveCandidates(@PathVariable Long importId,
                                             @Valid @RequestBody CandidateSelectionRequest request) {
        return importService.approveCandidates(importId, request);
    }

    @PostMapping("/{importId}/reject")
    @Operation(summary = "Reject candidates", description = "Rejected candidates never become items.")
    public RejectResponse rejectCandidates(@PathVariable Long importId,
                                           @Valid @RequestBody CandidateSelectionRequest request) {
        return importService.rejectCandidates(importId, request);
    }
}
