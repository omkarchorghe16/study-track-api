package com.studytrack.api.llm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings under {@code app.llm.*}; defaults and environment variables are in {@code application.yml}.
 * A user's saved settings (table {@code llm_settings}) choose provider, model and base URL; these are
 * the defaults. API keys exist only here, read from the environment, and are never stored or returned.
 *
 * @param provider         provider used when the user has saved no settings
 * @param timeout          read timeout of one LLM call (connect timeout is 10 seconds)
 * @param maxChunkTokens   largest document chunk sent in one call, estimated at 4 characters per token
 * @param maxChunksPerJob  documents that need more chunks are rejected, which caps the cost of one import
 * @param maxOutputTokens  {@code max_tokens} of one call
 * @param retryDelay       pause before the one retry of a rate-limited or failed (5xx, timeout) call
 */
@Validated
@ConfigurationProperties("app.llm")
public record LlmProperties(
        @NotNull LlmProvider provider,
        @NotNull Duration timeout,
        @Positive int maxChunkTokens,
        @Positive int maxChunksPerJob,
        @Positive int maxOutputTokens,
        @NotNull Duration retryDelay,
        @Valid @NotNull Anthropic anthropic,
        @Valid @NotNull OpenAi openai,
        @Valid @NotNull Ollama ollama) {

    /**
     * @param effort {@code output_config.effort} sent with each call (low, medium, high, ...); blank omits it
     */
    public record Anthropic(@NotBlank String baseUrl, @NotBlank String model, String apiKey, String effort) {
    }

    public record OpenAi(@NotBlank String baseUrl, @NotBlank String model, String apiKey) {
    }

    /** @param contextTokens {@code num_ctx} for the model; chunk plus answer must fit */
    public record Ollama(@NotBlank String baseUrl, @NotBlank String model, @PositiveOrZero int contextTokens) {
    }

    public String defaultModel(LlmProvider llmProvider) {
        return switch (llmProvider) {
            case ANTHROPIC -> anthropic.model();
            case OPENAI -> openai.model();
            case OLLAMA -> ollama.model();
        };
    }

    public String defaultBaseUrl(LlmProvider llmProvider) {
        return switch (llmProvider) {
            case ANTHROPIC -> anthropic.baseUrl();
            case OPENAI -> openai.baseUrl();
            case OLLAMA -> ollama.baseUrl();
        };
    }

    /** The key from the environment, or null; Ollama needs none. */
    public String apiKey(LlmProvider llmProvider) {
        String apiKey = switch (llmProvider) {
            case ANTHROPIC -> anthropic.apiKey();
            case OPENAI -> openai.apiKey();
            case OLLAMA -> null;
        };
        return apiKey == null || apiKey.isBlank() ? null : apiKey.strip();
    }
}
