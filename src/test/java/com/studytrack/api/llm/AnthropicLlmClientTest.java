package com.studytrack.api.llm;

import com.studytrack.api.llm.LlmDtos.LlmExtractionRequest;
import com.studytrack.api.llm.LlmDtos.LlmExtractionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AnthropicLlmClientTest {
    private static final LlmExtractionRequest REQUEST = new LlmExtractionRequest("system rules", "the excerpt",
            Map.of("type", "object"), null, null);

    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void createServer() {
        builder = RestClient.builder().baseUrl("https://anthropic.test").defaultHeader("x-api-key", "test-key");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void sendsStructuredOutputRequestWithFallbackAndReadsTextAndUsage() {
        server.expect(requestTo("https://anthropic.test/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(header("anthropic-version", "2026-10-21"))
                .andExpect(header("anthropic-beta", "server-side-fallback-2026-07-01"))
                .andExpect(jsonPath("$.model").value("claude-opus-5-5"))
                .andExpect(jsonPath("$.max_tokens").value(16000))
                .andExpect(jsonPath("$.system").value("system rules"))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("the excerpt"))
                .andExpect(jsonPath("$.output_config.effort").value("medium"))
                .andExpect(jsonPath("$.output_config.format.type").value("json_schema"))
                .andExpect(jsonPath("$.output_config.format.schema.type").value("object"))
                .andExpect(jsonPath("$.fallbacks").value("default"))
                .andExpect(jsonPath("$.thinking").doesNotExist())
                .andRespond(withSuccess("""
                        {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                         "content":[{"type":"thinking","thinking":""},{"type":"text","text":"{\\"items\\":[]}"}],
                         "stop_reason":"end_turn","usage":{"input_tokens":120,"output_tokens":15}}""",
                        MediaType.APPLICATION_JSON));

        LlmExtractionResult result = client("claude-opus-5-5", "medium").extract(REQUEST);

        assertEquals("{\"items\":[]}", result.text());
        assertEquals(120, result.inputTokens());
        assertEquals(15, result.outputTokens());
        server.verify();
    }

    @Test
    void retrySendsPreviousAnswerAndCorrectionAndOmitsFallbackOnOtherModels() {
        server.expect(requestTo("https://anthropic.test/v1/messages"))
                .andExpect(headerDoesNotExist("anthropic-beta"))
                .andExpect(jsonPath("$.fallbacks").doesNotExist())
                .andExpect(jsonPath("$.output_config.effort").doesNotExist())
                .andExpect(jsonPath("$.messages.length()").value(3))
                .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.messages[1].content").value("bad"))
                .andExpect(jsonPath("$.messages[2].content").value("fix it"))
                .andRespond(withSuccess("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],\"stop_reason\":\"end_turn\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals("ok", client("claude-haiku-5-5", " ").extract(REQUEST.withCorrection("bad", "fix it")).text());
        server.verify();
    }

    @Test
    void refusalAndTruncationArePromptSpecificFailures() {
        server.expect(requestTo("https://anthropic.test/v1/messages"))
                .andRespond(withSuccess("{\"content\":[],\"stop_reason\":\"refusal\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://anthropic.test/v1/messages"))
                .andRespond(withSuccess("{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"it\"}],\"stop_reason\":\"max_tokens\"}",
                        MediaType.APPLICATION_JSON));
        AnthropicLlmClient client = client("claude-opus-5-5", "medium");

        LlmException refused = assertThrows(LlmException.class, () -> client.extract(REQUEST));
        LlmException truncated = assertThrows(LlmException.class, () -> client.extract(REQUEST));

        assertEquals(LlmException.Reason.REFUSED, refused.getReason());
        assertEquals(LlmException.Reason.TRUNCATED, truncated.getReason());
        assertTrue(refused.isPromptSpecific());
    }

    @Test
    void httpErrorsBecomeSafeTypedFailures() {
        server.expect(requestTo("https://anthropic.test/v1/messages"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}"));
        server.expect(requestTo("https://anthropic.test/v1/messages"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("https://anthropic.test/v1/messages"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"output_config.format: unsupported\\nfor model\"}}"));
        AnthropicLlmClient client = client("claude-opus-5-5", "medium");

        LlmException unauthorized = assertThrows(LlmException.class, () -> client.extract(REQUEST));
        LlmException rateLimited = assertThrows(LlmException.class, () -> client.extract(REQUEST));
        LlmException rejected = assertThrows(LlmException.class, () -> client.extract(REQUEST));

        assertEquals(LlmException.Reason.CONFIGURATION, unauthorized.getReason());
        assertEquals("Anthropic rejected the API key (HTTP 401): invalid x-api-key", unauthorized.getMessage());
        assertFalse(unauthorized.getMessage().contains("test-key"));
        assertTrue(rateLimited.isRetryable());
        assertEquals(LlmException.Reason.REJECTED, rejected.getReason());
        assertEquals("Anthropic rejected the request (HTTP 400): output_config.format: unsupported for model",
                rejected.getMessage());
    }

    @Test
    void connectionTestReadsTheModelWithoutGenerating() {
        server.expect(requestTo("https://anthropic.test/v1/models/claude-opus-5-5"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("anthropic-version", "2026-10-21"))
                .andRespond(withSuccess("{\"id\":\"claude-opus-5-5\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://anthropic.test/v1/models/claude-opus-5-5"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        AnthropicLlmClient client = client("claude-opus-5-5", "medium");

        assertTrue(client.testConnection().ok());
        LlmDtos.LlmConnectionCheck missing = client.testConnection();
        assertFalse(missing.ok());
        assertTrue(missing.message().contains("HTTP 404"));
    }

    private AnthropicLlmClient client(String model, String effort) {
        return new AnthropicLlmClient(builder.build(), model, effort, 16000);
    }

}
