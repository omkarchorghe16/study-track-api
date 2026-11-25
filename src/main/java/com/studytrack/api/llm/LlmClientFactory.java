package com.studytrack.api.llm;

import com.studytrack.api.llm.LlmDtos.LlmConnectionSettings;

/** Builds the {@link LlmClient} for a provider, model and base URL. Tests replace it with a fake. */
public interface LlmClientFactory {

    /** @throws LlmException with reason CONFIGURATION when the provider's API key is not set */
    LlmClient createClient(LlmConnectionSettings settings);
}
