package com.studytrack.api.catalog;

import com.studytrack.api.catalog.CatalogDtos.CatalogSubjectResponse;
import com.studytrack.api.catalog.CatalogDtos.CatalogTopicNodeResponse;
import com.studytrack.api.catalog.CatalogDtos.CatalogTrackResponse;
import com.studytrack.api.subject.SubjectDtos.SubjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "Catalog", description = "Shared tracks, subjects and topics that users can copy into their own subjects")
public class CatalogController {
    private static final String CSV_TEMPLATE = """
            track,subject,topic_path,question,answer,difficulty
            Full-Stack Java Developer,Core Java,Collections,,,
            Full-Stack Java Developer,Core Java,Collections > HashMap,,,
            Full-Stack Java Developer,Core Java,Collections > HashMap,What is the default load factor of a HashMap?,"0.75: the table doubles in size when it is 75% full.",EASY
            Full-Stack Java Developer,Core Java,Multithreading,,,
            Full-Stack Java Developer,Spring Boot,,,,
            """;

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/tracks")
    @Operation(summary = "List tracks", description = "All catalog tracks with their subject counts.")
    public List<CatalogTrackResponse> listTracks() {
        return catalogService.listTracks();
    }

    @GetMapping("/tracks/{trackId}/subjects")
    @Operation(summary = "List the subjects of a track",
            description = "With topic counts, and the id of the user's own subject when it was already added.")
    @ApiResponse(responseCode = "404", description = "No such track")
    public List<CatalogSubjectResponse> listTrackSubjects(@PathVariable Long trackId) {
        return catalogService.listTrackSubjects(trackId);
    }

    @GetMapping("/subjects/{catalogSubjectId}/topics")
    @Operation(summary = "Get the topic tree of a catalog subject")
    @ApiResponse(responseCode = "404", description = "No such catalog subject")
    public List<CatalogTopicNodeResponse> listSubjectTopics(@PathVariable Long catalogSubjectId) {
        return catalogService.listSubjectTopics(catalogSubjectId);
    }

    @PostMapping("/subjects/{catalogSubjectId}/enroll")
    @Operation(summary = "Add a catalog subject to my subjects",
            description = "Creates the user's own subject with a copy of the whole topic tree and its starter questions.")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "404", description = "No such catalog subject")
    @ApiResponse(responseCode = "409", description = "Already added, or the user has a subject with this name")
    public ResponseEntity<SubjectResponse> enrollInSubject(@PathVariable Long catalogSubjectId) {
        SubjectResponse created = catalogService.enrollInSubject(catalogSubjectId);
        return ResponseEntity.created(URI.create("/api/v1/subjects/" + created.id())).body(created);
    }

    @GetMapping("/template.csv")
    @Operation(summary = "Download a sample catalog CSV",
            description = "Columns: track, subject, topic_path (levels separated by \">\"), and optionally question, answer, "
                    + "difficulty for a starter question. An admin uploads such a file.")
    public ResponseEntity<byte[]> getCsvTemplate() {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("catalog-template.csv").build().toString())
                .body(CSV_TEMPLATE.getBytes(StandardCharsets.UTF_8));
    }
}
