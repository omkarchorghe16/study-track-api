package com.studytrack.api.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * OpenAI Chat Completions ({@code POST /v1/chat/completions}) with a strict JSON Schema response format.
 * Also the base of {@link GroqLlmClient}, whose API has the same shape.
 */
public class OpenAiLlmClient implements LlmClient {
    private final RestClient restClient;
    private final String providerName;
    private final String model;
    private final int maxOutputTokens;

    /** {@code restClient} must already carry the base URL, timeouts and the bearer token. */
    public OpenAiLlmClient(RestClient restClient, String model, int maxOutputTokens) {
        this(restClient, "OpenAI", model, maxOutputTokens);
    }

    /** @param providerName name used in error and connection messages */
    protected OpenAiLlmClient(RestClient restClient, String providerName, String model, int maxOutputTokens) {
        this.restClient = restClient;
        this.providerName = providerName;
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
    }

    record ChatRequest(
            String model,
            List<ChatMessage> messages,
            @JsonProperty("max_completion_tokens") int maxCompletionTokens,
            @JsonProperty("response_format") ResponseFormat responseFormat) {
    }

    record ChatMessage(String role, String content) {
    }

    record ResponseFormat(String type, @JsonProperty("json_schema") JsonSchema jsonSchema) {
    }

    record JsonSchema(String name, boolean strict, Map<String, Object> schema) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(ChoiceMessage message, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChoiceMessage(String content, String refusal) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("prompt_tokens") long promptTokens,
                 @JsonProperty("completion_tokens") long completionTokens) {
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
        ChatRequest body = new ChatRequest(model, messages, maxOutputTokens,
                new ResponseFormat("json_schema", new JsonSchema("study_items", true, request.outputSchema())));
        ChatResponse response;
        try {
            response = sendChatRequest(body);
        } catch (RestClientException exception) {
            throw LlmErrors.translateFailure(providerName, exception);
        }
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null) {
            throw new LlmException(LlmException.Reason.UNAVAILABLE, providerName + " returned an empty answer");
        }
        Choice choice = response.choices().getFirst();
        if (choice.message().refusal() != null) {
            throw new LlmException(LlmException.Reason.REFUSED, "The model declined to process this part of the document");
        }
        if ("length".equals(choice.finishReason())) {
            throw new LlmException(LlmException.Reason.TRUNCATED,
                    "The answer for this part was cut off at the output token limit");
        }
        long inputTokens = response.usage() == null ? 0 : response.usage().promptTokens();
        long outputTokens = response.usage() == null ? 0 : response.usage().completionTokens();
        String text = choice.message().content() == null ? "" : choice.message().content();
        return new LlmExtractionResult(text, inputTokens, outputTokens);
    }

    /** One Chat Completions call; HTTP failures surface as {@link RestClientException}. */
    protected ChatResponse sendChatRequest(ChatRequest body) {
        return restClient.post().uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(ChatResponse.class);
    }

    @Override
    public LlmConnectionCheck testConnection() {
        try {
            restClient.get().uri("/v1/models/{model}", model).retrieve().toBodilessEntity();
            return new LlmConnectionCheck(true, "Connected to " + providerName + "; model " + model + " is available");
        } catch (RestClientException exception) {
            return new LlmConnectionCheck(false, LlmErrors.translateFailure(providerName, exception).getMessage());
        }
    }

    protected RestClient getRestClient() {
        return restClient;
    }

    protected String getProviderName() {
        return providerName;
    }

    protected String getModel() {
        return model;
    }
}
