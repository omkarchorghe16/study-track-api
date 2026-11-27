package com.studytrack.api.llm;

/**
 * A failed LLM call. The message is safe to show to the user: it never contains the API key, the
 * prompt or the provider's raw response.
 */
public class LlmException extends RuntimeException {

    public enum Reason {
        /** Missing API key, bad base URL or an unknown model; every further call would fail too. */
        CONFIGURATION,
        /** The provider rejected the request (4xx other than 429). */
        REJECTED,
        /** Rate limited, 5xx or a network failure; worth one retry. */
        UNAVAILABLE,
        /** The model declined to answer this prompt. */
        REFUSED,
        /** The answer hit the output token limit and is incomplete. */
        TRUNCATED,
        /** The provider could not produce an answer matching the JSON Schema for this prompt. */
        INVALID_ANSWER
    }

    private final Reason reason;

    public LlmException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public LlmException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    /** True when the same call may succeed if sent again shortly. */
    public boolean isRetryable() {
        return reason == Reason.UNAVAILABLE;
    }

    /** True when the failure is specific to one prompt, so other chunks of a document can still be processed. */
    public boolean isPromptSpecific() {
        return reason == Reason.REFUSED || reason == Reason.TRUNCATED || reason == Reason.INVALID_ANSWER;
    }
}
