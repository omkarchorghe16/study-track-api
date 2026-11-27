package com.studytrack.api.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.studytrack.api.llm.LlmDtos.LlmConnectionCheck;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

/**
 * Groq's OpenAI-compatible Chat Completions API (base URL {@code https://api.groq.com/openai}). Requests and
 * answers have the OpenAI shape; the differences are handled here:
 * <ul>
 *   <li>A rate-limited call (HTTP 429, usually the free tier's tokens per minute) waits for the
 *       {@code Retry-After} time, at most {@value #MAX_WAIT_SECONDS} s, and is sent again, up to
 *       {@value #MAX_RATE_LIMIT_WAITS} times, so a long document is processed slowly instead of failing.</li>
 *   <li>Model ids contain a slash ({@code openai/gpt-oss-120b}), so the connection test looks the model up
 *       in {@code GET /v1/models} instead of calling {@code GET /v1/models/{model}}.</li>
 * </ul>
 */
public class GroqLlmClient extends OpenAiLlmClient {
    static final int MAX_RATE_LIMIT_WAITS = 3;
    static final long MAX_WAIT_SECONDS = 60;
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(10);

    /** Waits between rate-limited attempts; tests pass one that does not sleep. */
    @FunctionalInterface
    interface RateLimitPause {
        void pauseFor(Duration wait) throws InterruptedException;
    }

    private final RateLimitPause rateLimitPause;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelList(List<ModelEntry> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelEntry(String id) {
    }

    /** {@code restClient} must already carry the base URL, timeouts and the bearer token. */
    public GroqLlmClient(RestClient restClient, String model, int maxOutputTokens) {
        this(restClient, model, maxOutputTokens, wait -> Thread.sleep(wait.toMillis()));
    }

    GroqLlmClient(RestClient restClient, String model, int maxOutputTokens, RateLimitPause rateLimitPause) {
        super(restClient, "Groq", model, maxOutputTokens);
        this.rateLimitPause = rateLimitPause;
    }

    @Override
    protected ChatResponse sendChatRequest(ChatRequest body) {
        int waits = 0;
        while (true) {
            try {
                return super.sendChatRequest(body);
            } catch (HttpClientErrorException.TooManyRequests rateLimited) {
                if (waits == MAX_RATE_LIMIT_WAITS) {
                    throw rateLimited;
                }
                waits++;
                pauseFor(readRetryAfter(rateLimited));
            }
        }
    }

    @Override
    public LlmConnectionCheck testConnection() {
        try {
            ModelList models = getRestClient().get().uri("/v1/models").retrieve().body(ModelList.class);
            boolean modelFound = models != null && models.data() != null
                    && models.data().stream().anyMatch(entry -> getModel().equals(entry.id()));
            if (!modelFound) {
                return new LlmConnectionCheck(false,
                        "Connected to Groq, but model " + getModel() + " is not in your account's model list");
            }
            return new LlmConnectionCheck(true, "Connected to Groq; model " + getModel() + " is available");
        } catch (RestClientException exception) {
            return new LlmConnectionCheck(false, LlmErrors.translateFailure(getProviderName(), exception).getMessage());
        }
    }

    private void pauseFor(Duration wait) {
        try {
            rateLimitPause.pauseFor(wait);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.Reason.UNAVAILABLE, "The wait for Groq's rate limit was interrupted",
                    exception);
        }
    }

    /** Seconds from the {@code Retry-After} header, capped; a missing or unreadable value waits 10 s. */
    static Duration readRetryAfter(HttpClientErrorException rateLimited) {
        HttpHeaders headers = rateLimited.getResponseHeaders();
        String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) {
            return DEFAULT_WAIT;
        }
        try {
            double seconds = Double.parseDouble(retryAfter.strip());
            long waitMillis = (long) Math.ceil(Math.max(seconds, 1) * 1000);
            return Duration.ofMillis(Math.min(waitMillis, MAX_WAIT_SECONDS * 1000));
        } catch (NumberFormatException exception) {
            return DEFAULT_WAIT;
        }
    }
}
