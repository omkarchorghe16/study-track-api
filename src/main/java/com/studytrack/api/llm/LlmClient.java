package com.studytrack.api.llm;

import com.studytrack.api.llm.LlmDtos.LlmConnectionCheck;
import com.studytrack.api.llm.LlmDtos.LlmExtractionRequest;
import com.studytrack.api.llm.LlmDtos.LlmExtractionResult;

/**
 * One LLM provider, called over HTTP with Spring {@code RestClient} (no vendor SDKs). Implementations
 * translate provider errors into {@link LlmException} and never log keys, prompts or answers.
 */
public interface LlmClient {

    /** Sends the prompt and returns the model's text answer, which should be JSON matching the request's schema. */
    LlmExtractionResult extract(LlmExtractionRequest request);

    /** Checks that the service answers, the key is accepted and the model exists, without generating text. */
    LlmConnectionCheck testConnection();
}
