package com.studytrack.api.topic;

import com.studytrack.api.topic.TopicDtos.CreateTopicRequest;
import com.studytrack.api.topic.TopicDtos.MoveTopicRequest;
import com.studytrack.api.topic.TopicDtos.ReplaceTopicRequest;
import com.studytrack.api.topic.TopicDtos.TopicNodeResponse;
import com.studytrack.api.topic.TopicDtos.TopicResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Topic routes; the tree is read under its subject, everything else under {@code /topics}. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Topics", description = "The topic tree inside a subject")
public class TopicController {
    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping("/subjects/{subjectId}/topics")
    @Operation(summary = "Get a subject's topic tree", description = "Top-level topics with nested children and item counts.")
    @ApiResponse(responseCode = "404", description = "No such subject for the current user")
    public List<TopicNodeResponse> listTopicTree(@PathVariable Long subjectId) {
        return topicService.listTopicTree(subjectId);
    }

    @GetMapping("/topics/{topicId}")
    @Operation(summary = "Get a topic")
    public TopicResponse getTopic(@PathVariable Long topicId) {
        return topicService.getTopic(topicId);
    }

    @PostMapping("/topics")
    @Operation(summary = "Create a topic")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Validation failed or the parent is in another subject")
    public ResponseEntity<TopicResponse> createTopic(@Valid @RequestBody CreateTopicRequest request) {
        TopicResponse created = topicService.createTopic(request);
        return ResponseEntity.created(URI.create("/api/v1/topics/" + created.id())).body(created);
    }

    @PutMapping("/topics/{topicId}")
    @Operation(summary = "Replace a topic's name, description and sort order")
    public TopicResponse replaceTopic(@PathVariable Long topicId, @Valid @RequestBody ReplaceTopicRequest request) {
        return topicService.replaceTopic(topicId, request);
    }

    @PatchMapping("/topics/{topicId}/move")
    @Operation(summary = "Move a topic", description = "Sets a new parent (null for top level) and sort order within the same subject.")
    @ApiResponse(responseCode = "400", description = "Parent is in another subject, or is the topic itself or a subtopic")
    public TopicResponse moveTopic(@PathVariable Long topicId, @Valid @RequestBody MoveTopicRequest request) {
        return topicService.moveTopic(topicId, request);
    }

    @DeleteMapping("/topics/{topicId}")
    @Operation(summary = "Delete a topic", description = "Also deletes its subtopics and all their items.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    public ResponseEntity<Void> deleteTopic(@PathVariable Long topicId) {
        topicService.deleteTopic(topicId);
        return ResponseEntity.noContent().build();
    }
}
