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
import java.util.Objects;
import java.util.Set;

/**
 * Claude Messages API ({@code POST /v1/messages}). The answer is constrained to the request's JSON
 * Schema with structured output ({@code output_config.format}). On the models that support it, the
 * server-side refusal fallback is switched on, so a false-positive safety decline is retried on the model
 * Anthropic recommends instead of failing the chunk.
 */
public class AnthropicLlmClient implements LlmClient {
    static final String API_VERSION = "2026-10-21";
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    /** Models documented to accept {@code fallbacks}; others would reject the field. */
    private static final Set<String> FALLBACK_MODELS = Set.of(
            "claude-fable-5-2", "claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5");
    private static final String PROVIDER_NAME = "Anthropic";

    private final RestClient restClient;
    private final String model;
    private final String effort;
    private final int maxOutputTokens;

    /** {@code restClient} must already carry the base URL, timeouts and the {@code x-api-key} header. */
    public AnthropicLlmClient(RestClient restClient, String model, String effort, int maxOutputTokens) {
        this.restClient = restClient;
        this.model = model;
        this.effort = effort == null || effort.isBlank() ? null : effort.strip();
        this.maxOutputTokens = maxOutputTokens;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MessagesRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            String system,
            List<Message> messages,
            @JsonProperty("output_config") OutputConfig outputConfig,
            String fallbacks) {
    }

    record Message(String role, String content) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OutputConfig(String effort, OutputFormat format) {
    }

    record OutputFormat(String type, Map<String, Object> schema) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MessagesResponse(
            String model,
            List<ContentBlock> content,
            @JsonProperty("stop_reason") String stopReason,
            Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String type, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("input_tokens") long inputTokens, @JsonProperty("output_tokens") long outputTokens) {
    }

    @Override
    public LlmExtractionResult extract(LlmExtractionRequest request) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("user", request.userPrompt()));
        if (request.isRetry()) {
            messages.add(new Message("assistant", request.previousAnswer()));
            messages.add(new Message("user", request.correction()));
        }
        boolean fallbackSupported = FALLBACK_MODELS.contains(model);
        MessagesRequest body = new MessagesRequest(model, maxOutputTokens, request.systemPrompt(), messages,
                new OutputConfig(effort, new OutputFormat("json_schema", request.outputSchema())),
                fallbackSupported ? "default" : null);
        MessagesResponse response;
        try {
            RestClient.RequestBodySpec spec = restClient.post().uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("anthropic-version", API_VERSION);
            if (fallbackSupported) {
                spec = spec.header("anthropic-beta", FALLBACK_BETA);
            }
            response = spec.body(body).retrieve().body(MessagesResponse.class);
        } catch (RestClientException exception) {
            throw LlmErrors.translateFailure(PROVIDER_NAME, exception);
        }
        return toResult(response);
    }

    private static LlmExtractionResult toResult(MessagesResponse response) {
        if (response == null || response.content() == null) {
            throw new LlmException(LlmException.Reason.UNAVAILABLE, "Anthropic returned an empty answer");
        }
        if ("refusal".equals(response.stopReason())) {
            throw new LlmException(LlmException.Reason.REFUSED, "Claude declined to process this part of the document");
        }
        if ("max_tokens".equals(response.stopReason())) {
            throw new LlmException(LlmException.Reason.TRUNCATED,
                    "Claude's answer for this part was cut off at the output token limit");
        }
        String text = String.join("", response.content().stream()
                .filter(block -> "text".equals(block.type()))
                .map(ContentBlock::text)
                .filter(Objects::nonNull)
                .toList());
        long inputTokens = response.usage() == null ? 0 : response.usage().inputTokens();
        long outputTokens = response.usage() == null ? 0 : response.usage().outputTokens();
        return new LlmExtractionResult(text, inputTokens, outputTokens);
    }

    /** {@code GET /v1/models/{model}}: checks key and model without generating (and paying for) tokens. */
    @Override
    public LlmConnectionCheck testConnection() {
        try {
            restClient.get().uri("/v1/models/{model}", model)
                    .header("anthropic-version", API_VERSION)
                    .retrieve().toBodilessEntity();
            return new LlmConnectionCheck(true, "Connected to Anthropic; model " + model + " is available");
        } catch (RestClientException exception) {
            return new LlmConnectionCheck(false, LlmErrors.translateFailure(PROVIDER_NAME, exception).getMessage());
        }
    }
}
