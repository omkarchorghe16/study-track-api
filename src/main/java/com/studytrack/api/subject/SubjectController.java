package com.studytrack.api.subject;

import com.studytrack.api.subject.SubjectDtos.SubjectRequest;
import com.studytrack.api.subject.SubjectDtos.SubjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/subjects")
@Tag(name = "Subjects", description = "Courses or prep tracks that hold a topic tree")
public class SubjectController {
    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    @Operation(summary = "List subjects", description = "All subjects of the current user, by sort order then name, with topic and item counts.")
    public List<SubjectResponse> listSubjects() {
        return subjectService.listSubjects();
    }

    @GetMapping("/{subjectId}")
    @Operation(summary = "Get a subject")
    @ApiResponse(responseCode = "404", description = "No such subject for the current user")
    public SubjectResponse getSubject(@PathVariable Long subjectId) {
        return subjectService.getSubject(subjectId);
    }

    @PostMapping
    @Operation(summary = "Create a subject")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "409", description = "The user already has a subject with this name")
    public ResponseEntity<SubjectResponse> createSubject(@Valid @RequestBody SubjectRequest request) {
        SubjectResponse created = subjectService.createSubject(request);
        return ResponseEntity.created(URI.create("/api/v1/subjects/" + created.id())).body(created);
    }

    @PutMapping("/{subjectId}")
    @Operation(summary = "Replace a subject")
    @ApiResponse(responseCode = "409", description = "The user already has another subject with this name")
    public SubjectResponse replaceSubject(@PathVariable Long subjectId, @Valid @RequestBody SubjectRequest request) {
        return subjectService.replaceSubject(subjectId, request);
    }

    @DeleteMapping("/{subjectId}")
    @Operation(summary = "Delete a subject", description = "Also deletes all its topics and items.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    public ResponseEntity<Void> deleteSubject(@PathVariable Long subjectId) {
        subjectService.deleteSubject(subjectId);
        return ResponseEntity.noContent().build();
    }
}
