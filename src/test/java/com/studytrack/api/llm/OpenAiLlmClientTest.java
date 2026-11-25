package com.studytrack.api.llm;

import com.studytrack.api.llm.LlmDtos.LlmExtractionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiLlmClientTest {

    @Test
    void sendsStrictJsonSchemaAndHandlesRefusal() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://openai.test")
                .defaultHeader("Authorization", "Bearer test-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://openai.test/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.max_completion_tokens").value(16000))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"items\\":[]}"},"finish_reason":"stop"}],
                         "usage":{"prompt_tokens":30,"completion_tokens":5}}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openai.test/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"refusal\":\"no\"},\"finish_reason\":\"stop\"}]}",
                        MediaType.APPLICATION_JSON));
        OpenAiLlmClient client = new OpenAiLlmClient(builder.build(), "gpt-4o-mini", 16000);
        LlmExtractionRequest request = new LlmExtractionRequest("rules", "excerpt", Map.of("type", "object"), null, null);

        assertEquals(30, client.extract(request).inputTokens());
        LlmException refused = assertThrows(LlmException.class, () -> client.extract(request));
        assertEquals(LlmException.Reason.REFUSED, refused.getReason());
    }
}
