package com.studytrack.api.llm;

/** LLM services an import can use. Each has its own {@link LlmClient}. */
public enum LlmProvider {
    ANTHROPIC(true),
    OPENAI(true),
    GROQ(true),
    OLLAMA(false);

    private final boolean apiKeyRequired;

    LlmProvider(boolean apiKeyRequired) {
        this.apiKeyRequired = apiKeyRequired;
    }

    public boolean isApiKeyRequired() {
        return apiKeyRequired;
    }
}
