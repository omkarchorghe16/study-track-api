package com.studytrack.api.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Turns {@code RestClient} failures into {@link LlmException}s with safe messages. Only the provider's
 * short error message is kept (truncated, control characters removed); response bodies are never logged.
 */
final class LlmErrors {
    private static final int MAX_PROVIDER_MESSAGE_LENGTH = 200;
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

    private LlmErrors() {
    }

    /** Error body of Anthropic and OpenAI: {@code {"error": {"type": ..., "message": ...}}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record NestedErrorBody(NestedError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NestedError(String type, String message) {
    }

    /** Error body of Ollama: {@code {"error": "..."}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FlatErrorBody(String error) {
    }

    static LlmException translateFailure(String providerName, RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return translateStatus(providerName, responseException);
        }
        if (exception instanceof ResourceAccessException) {
            return new LlmException(LlmException.Reason.UNAVAILABLE,
                    providerName + " could not be reached or did not answer in time", exception);
        }
        return new LlmException(LlmException.Reason.UNAVAILABLE,
                providerName + " returned an answer that could not be read", exception);
    }

    private static LlmException translateStatus(String providerName, RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String detail = readProviderMessage(exception.getResponseBodyAsString());
        String suffix = detail == null ? "" : ": " + detail;
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            return new LlmException(LlmException.Reason.CONFIGURATION,
                    providerName + " rejected the API key (HTTP " + status + ")" + suffix, exception);
        }
        if (status == HttpStatus.NOT_FOUND.value()) {
            return new LlmException(LlmException.Reason.CONFIGURATION,
                    providerName + " does not know this model or URL (HTTP 404)" + suffix, exception);
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value() || status >= 500) {
            return new LlmException(LlmException.Reason.UNAVAILABLE,
                    providerName + " is busy or failing (HTTP " + status + ")" + suffix, exception);
        }
        return new LlmException(LlmException.Reason.REJECTED,
                providerName + " rejected the request (HTTP " + status + ")" + suffix, exception);
    }

    /** The provider's own error message, if its body has one of the known shapes. */
    static String readProviderMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String message = null;
        try {
            NestedErrorBody nested = ERROR_MAPPER.readValue(body, NestedErrorBody.class);
            if (nested.error() != null) {
                message = nested.error().message();
            }
        } catch (Exception nestedFailure) {
            try {
                message = ERROR_MAPPER.readValue(body, FlatErrorBody.class).error();
            } catch (Exception flatFailure) {
                return null;
            }
        }
        return cleanMessage(message);
    }

    static String cleanMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String cleaned = message.replaceAll("\\p{Cntrl}", " ").strip();
        return cleaned.length() <= MAX_PROVIDER_MESSAGE_LENGTH
                ? cleaned
                : cleaned.substring(0, MAX_PROVIDER_MESSAGE_LENGTH) + "…";
    }
}
