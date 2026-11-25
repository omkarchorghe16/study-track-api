package com.studytrack.api.llm;

import com.studytrack.api.llm.LlmDtos.LlmConnectionSettings;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestLlmClientFactoryTest {

    @Test
    void requiresTheProviderKeyButNotForOllama() {
        RestLlmClientFactory factory = new RestLlmClientFactory(RestClient.builder(), properties(" "));

        LlmException missingKey = assertThrows(LlmException.class, () -> factory.createClient(
                new LlmConnectionSettings(LlmProvider.ANTHROPIC, "claude-opus-5-5", "https://api.anthropic.com")));
        assertEquals(LlmException.Reason.CONFIGURATION, missingKey.getReason());
        assertEquals("ANTHROPIC_API_KEY is not set on the server", missingKey.getMessage());

        assertInstanceOf(OllamaLlmClient.class, factory.createClient(
                new LlmConnectionSettings(LlmProvider.OLLAMA, "llama3.1", "http://localhost:11434")));
        assertInstanceOf(AnthropicLlmClient.class, new RestLlmClientFactory(RestClient.builder(), properties("key"))
                .createClient(new LlmConnectionSettings(LlmProvider.ANTHROPIC, "claude-opus-5-5", "https://api.anthropic.com")));
    }

    private static LlmProperties properties(String anthropicKey) {
        return new LlmProperties(LlmProvider.ANTHROPIC, Duration.ofMinutes(5), 6000, 200, 16000, Duration.ZERO,
                new LlmProperties.Anthropic("https://api.anthropic.com", "claude-opus-5-5", anthropicKey, "medium"),
                new LlmProperties.OpenAi("https://api.openai.com", "gpt-4o-mini", null),
                new LlmProperties.Ollama("http://localhost:11434", "llama3.1", 16384));
    }
}
