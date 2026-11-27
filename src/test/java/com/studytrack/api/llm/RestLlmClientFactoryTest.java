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

    @Test
    void createsGroqClientWithItsOwnKeyAndLimits() {
        LlmConnectionSettings groq = new LlmConnectionSettings(LlmProvider.GROQ, "openai/gpt-oss-120b",
                "https://api.groq.com/openai");
        LlmException missingKey = assertThrows(LlmException.class, () ->
                new RestLlmClientFactory(RestClient.builder(), properties("key")).createClient(groq));
        assertEquals("GROQ_API_KEY is not set on the server", missingKey.getMessage());

        LlmProperties withGroqKey = properties(null, "groq-key");
        assertInstanceOf(GroqLlmClient.class,
                new RestLlmClientFactory(RestClient.builder(), withGroqKey).createClient(groq));
        assertEquals(1500, withGroqKey.maxChunkTokens(LlmProvider.GROQ));
        assertEquals(6000, withGroqKey.maxChunkTokens(LlmProvider.ANTHROPIC));
        assertEquals(4000, withGroqKey.maxOutputTokens(LlmProvider.GROQ));
        assertEquals(16000, withGroqKey.maxOutputTokens(LlmProvider.OPENAI));
    }

    private static LlmProperties properties(String anthropicKey) {
        return properties(anthropicKey, null);
    }

    private static LlmProperties properties(String anthropicKey, String groqKey) {
        return new LlmProperties(LlmProvider.ANTHROPIC, Duration.ofMinutes(5), 6000, 200, 16000, Duration.ZERO,
                new LlmProperties.Anthropic("https://api.anthropic.com", "claude-opus-5-5", anthropicKey, "medium"),
                new LlmProperties.OpenAi("https://api.openai.com", "gpt-4o-mini", null),
                new LlmProperties.Groq("https://api.groq.com/openai", "openai/gpt-oss-120b", groqKey, 1500, 4000),
                new LlmProperties.Ollama("http://localhost:11434", "llama3.1", 16384));
    }
}
