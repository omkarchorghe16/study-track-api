package com.studytrack.api.llm;

import com.studytrack.api.llm.LlmDtos.LlmConnectionCheck;
import com.studytrack.api.llm.LlmDtos.LlmExtractionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GroqLlmClientTest {
    private static final String CHAT_URL = "https://api.groq.test/openai/v1/chat/completions";
    private static final String MODEL = "openai/gpt-oss-120b";
    private static final String ANSWER = """
            {"choices":[{"message":{"content":"{\\"items\\":[]}"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":40,"completion_tokens":7}}""";

    private final LlmExtractionRequest request =
            new LlmExtractionRequest("rules", "excerpt", Map.of("type", "object"), null, null);

    @Test
    void sendsOpenAiShapedRequestToGroq() {
        RestClient.Builder builder = groqBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(CHAT_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.max_completion_tokens").value(4000))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));
        GroqLlmClient client = new GroqLlmClient(builder.build(), MODEL, 4000, wait -> { });

        assertEquals(40, client.extract(request).inputTokens());
        server.verify();
    }

    @Test
    void waitsForRetryAfterWhenRateLimited() {
        RestClient.Builder builder = groqBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(CHAT_URL)).andRespond(rateLimited("7"));
        server.expect(requestTo(CHAT_URL)).andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));
        List<Duration> waits = new ArrayList<>();
        GroqLlmClient client = new GroqLlmClient(builder.build(), MODEL, 4000, waits::add);

        assertEquals(7, client.extract(request).outputTokens());
        assertEquals(List.of(Duration.ofSeconds(7)), waits);
        server.verify();
    }

    @Test
    void givesUpAfterTheLastRateLimitWait() {
        RestClient.Builder builder = groqBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(GroqLlmClient.MAX_RATE_LIMIT_WAITS + 1), requestTo(CHAT_URL)).andRespond(rateLimited("600"));
        List<Duration> waits = new ArrayList<>();
        GroqLlmClient client = new GroqLlmClient(builder.build(), MODEL, 4000, waits::add);

        LlmException failure = assertThrows(LlmException.class, () -> client.extract(request));
        assertEquals(LlmException.Reason.UNAVAILABLE, failure.getReason());
        assertTrue(failure.getMessage().startsWith("Groq is busy or failing (HTTP 429)"));
        assertEquals(GroqLlmClient.MAX_RATE_LIMIT_WAITS, waits.size());
        assertEquals(Duration.ofSeconds(GroqLlmClient.MAX_WAIT_SECONDS), waits.getFirst());
    }

    @Test
    void jsonValidationFailureIsSentOnceMoreThenSkipsThePart() {
        RestClient.Builder builder = groqBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String invalidJson = "{\"error\":{\"message\":\"Failed to generate JSON.\",\"code\":\"json_validate_failed\"}}";
        server.expect(requestTo(CHAT_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body(invalidJson));
        server.expect(requestTo(CHAT_URL)).andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));
        server.expect(times(2), requestTo(CHAT_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body(invalidJson));
        GroqLlmClient client = new GroqLlmClient(builder.build(), MODEL, 4000, wait -> { });

        assertEquals(40, client.extract(request).inputTokens());
        LlmException skipped = assertThrows(LlmException.class, () -> client.extract(request));
        assertEquals(LlmException.Reason.INVALID_ANSWER, skipped.getReason());
        assertTrue(skipped.isPromptSpecific());
        server.verify();
    }

    @Test
    void connectionTestFindsTheModelInTheList() {
        RestClient.Builder builder = groqBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String models = """
                {"object":"list","data":[{"id":"openai/gpt-oss-20b"},{"id":"openai/gpt-oss-120b"}]}""";
        server.expect(times(2), requestTo("https://api.groq.test/openai/v1/models"))
                .andRespond(withSuccess(models, MediaType.APPLICATION_JSON));

        LlmConnectionCheck found = new GroqLlmClient(builder.build(), MODEL, 4000, wait -> { }).testConnection();
        LlmConnectionCheck missing = new GroqLlmClient(builder.build(), "unknown-model", 4000, wait -> { }).testConnection();

        assertTrue(found.ok());
        assertFalse(missing.ok());
        assertTrue(missing.message().contains("unknown-model"));
    }

    private static RestClient.Builder groqBuilder() {
        return RestClient.builder().baseUrl("https://api.groq.test/openai")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-key");
    }

    private static org.springframework.test.web.client.ResponseCreator rateLimited(String retryAfterSeconds) {
        return withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"Rate limit reached for tokens per minute\"}}");
    }
}
