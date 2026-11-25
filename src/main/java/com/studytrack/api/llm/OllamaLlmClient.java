package com.studytrack.api.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.studytrack.api.llm.LlmDtos.LlmConnectionCheck;
import com.studytrack.api.llm.LlmDtos.LlmExtractionRequest;
import com.studytrack.api.llm.LlmDtos.LlmExtractionResult;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A local Ollama server ({@code POST /api/chat}, no streaming). Free and offline, good for trying imports;
 * the JSON Schema goes in {@code format} so the model answers with matching JSON.
 */
public class OllamaLlmClient implements LlmClient {
    private static final String PROVIDER_NAME = "Ollama";

    private final RestClient restClient;
    private final String model;
    private final int contextTokens;

    public OllamaLlmClient(RestClient restClient, String model, int contextTokens) {
        this.restClient = restClient;
        this.model = model;
        this.contextTokens = contextTokens;
    }

    record ChatRequest(String model, List<ChatMessage> messages, boolean stream, Map<String, Object> format,
                       Options options) {
    }

    record ChatMessage(String role, String content) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Options(@JsonProperty("num_ctx") Integer numCtx, double temperature) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(ChatMessage message, @JsonProperty("done_reason") String doneReason,
                        @JsonProperty("prompt_eval_count") long promptEvalCount,
                        @JsonProperty("eval_count") long evalCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TagsResponse(List<ModelTag> models) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelTag(String name) {
    }

    @Override
    public LlmExtractionResult extract(LlmExtractionRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", request.systemPrompt()));
        messages.add(new ChatMessage("user", request.userPrompt()));
        if (request.isRetry()) {
            messages.add(new ChatMessage("assistant", request.previousAnswer()));
            messages.add(new ChatMessage("user", request.correction()));
        }
        Options options = new Options(contextTokens > 0 ? contextTokens : null, 0);
        ChatResponse response;
        try {
            response = restClient.post().uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ChatRequest(model, messages, false, request.outputSchema(), options))
                    .retrieve().body(ChatResponse.class);
        } catch (RestClientException exception) {
            throw LlmErrors.translateFailure(PROVIDER_NAME, exception);
        }
        if (response == null || response.message() == null || response.message().content() == null) {
            throw new LlmException(LlmException.Reason.UNAVAILABLE, "Ollama returned an empty answer");
        }
        if ("length".equals(response.doneReason())) {
            throw new LlmException(LlmException.Reason.TRUNCATED,
                    "The answer for this part was cut off at the model's length limit");
        }
        return new LlmExtractionResult(response.message().content(), response.promptEvalCount(), response.evalCount());
    }

    /** {@code GET /api/tags}: the server answers and has the model pulled. */
    @Override
    public LlmConnectionCheck testConnection() {
        TagsResponse tags;
        try {
            tags = restClient.get().uri("/api/tags").retrieve().body(TagsResponse.class);
        } catch (RestClientException exception) {
            return new LlmConnectionCheck(false, LlmErrors.translateFailure(PROVIDER_NAME, exception).getMessage());
        }
        List<ModelTag> models = tags == null || tags.models() == null ? List.of() : tags.models();
        boolean found = models.stream().map(ModelTag::name)
                .anyMatch(name -> model.equals(name) || (model + ":latest").equals(name));
        if (!found) {
            return new LlmConnectionCheck(false,
                    "Ollama is running but model " + model + " is not pulled (run: ollama pull " + model + ")");
        }
        return new LlmConnectionCheck(true, "Connected to Ollama; model " + model + " is available");
    }
}
