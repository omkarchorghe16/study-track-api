package com.studytrack.api.llm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class LlmDtos {
    private LlmDtos() {
    }

    /**
     * One extraction call. {@code outputSchema} is the JSON Schema the answer must match; providers that
     * support structured output enforce it. For the one retry after an invalid answer,
     * {@code previousAnswer} holds that answer and {@code correction} says what was wrong with it.
     */
    public record LlmExtractionRequest(
            String systemPrompt,
            String userPrompt,
            Map<String, Object> outputSchema,
            String previousAnswer,
            String correction) {

        public LlmExtractionRequest withCorrection(String answer, String correctionMessage) {
            return new LlmExtractionRequest(systemPrompt, userPrompt, outputSchema, answer, correctionMessage);
        }

        public boolean isRetry() {
            return previousAnswer != null && correction != null;
        }
    }

    /** The model's text answer and the tokens the call used. */
    public record LlmExtractionResult(String text, long inputTokens, long outputTokens) {
    }

    public record LlmConnectionCheck(boolean ok, String message) {
    }

    /** Provider, model and base URL one client talks to. */
    public record LlmConnectionSettings(LlmProvider provider, String model, String baseUrl) {
    }

    /**
     * Body of PUT /settings/llm and POST /settings/llm/test. A blank {@code baseUrl} means the provider's
     * default; a missing {@code enabled} means true. API keys are not accepted here: they come from
     * {@code ANTHROPIC_API_KEY} / {@code OPENAI_API_KEY}.
     */
    public record LlmSettingsRequest(
            @NotNull LlmProvider provider,
            @NotBlank @Size(max = 100) String model,
            @Size(max = 500) String baseUrl,
            Boolean enabled) {
    }

    /**
     * The current user's LLM settings. {@code saved} is false while the defaults from
     * {@code application.yml} apply. {@code providers} lists every provider's defaults and whether its
     * API key is configured on the server (the key itself is never returned).
     */
    public record LlmSettingsResponse(
            LlmProvider provider,
            String model,
            String baseUrl,
            boolean enabled,
            boolean saved,
            List<LlmProviderResponse> providers) {
    }

    public record LlmProviderResponse(
            LlmProvider provider,
            String defaultModel,
            String defaultBaseUrl,
            boolean apiKeyRequired,
            boolean apiKeyConfigured) {
    }

    public record LlmConnectionTestResponse(boolean ok, String message, LlmProvider provider, String model) {
    }
}
