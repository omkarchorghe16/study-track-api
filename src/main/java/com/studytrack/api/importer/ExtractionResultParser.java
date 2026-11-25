package com.studytrack.api.importer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.List;

/** Reads the LLM's JSON answer into typed items. Lenient about extra fields; strict about the shape. */
final class ExtractionResultParser {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .build();

    private ExtractionResultParser() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExtractionPayload(List<ExtractedItem> items) {
    }

    /** One item as the LLM returned it; any field can be missing. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExtractedItem(
            String topicPath,
            String type,
            String question,
            String answerHtml,
            String notesHtml,
            String difficulty,
            List<String> tags,
            List<Long> imageRefs,
            Double confidence) {
    }

    /** The answer is not usable JSON; {@code problem} is a short description safe to send back to the LLM. */
    static class InvalidExtractionException extends Exception {
        InvalidExtractionException(String problem) {
            super(problem);
        }
    }

    static List<ExtractedItem> parseItems(String answer) throws InvalidExtractionException {
        if (answer == null || answer.isBlank()) {
            throw new InvalidExtractionException("the reply was empty");
        }
        ExtractionPayload payload;
        try {
            payload = MAPPER.readValue(stripCodeFence(answer.strip()), ExtractionPayload.class);
        } catch (Exception exception) {
            throw new InvalidExtractionException("the reply could not be parsed as JSON");
        }
        if (payload == null || payload.items() == null) {
            throw new InvalidExtractionException("the \"items\" array is missing");
        }
        return payload.items().stream().filter(item -> item != null).toList();
    }

    /** Some local models wrap JSON in ```json fences despite the instructions. */
    static String stripCodeFence(String answer) {
        if (!answer.startsWith("```")) {
            return answer;
        }
        int firstLineEnd = answer.indexOf('\n');
        int closingFence = answer.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
            return answer;
        }
        return answer.substring(firstLineEnd + 1, closingFence).strip();
    }
}
