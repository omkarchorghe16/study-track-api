package com.studytrack.api.llm;

import com.studytrack.api.llm.LlmDtos.LlmExtractionRequest;
import com.studytrack.api.llm.LlmDtos.LlmExtractionResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaLlmClientTest {

    @Test
    void sendsSchemaAsFormatAndReadsAnswer() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ollama.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ollama.test/api/chat"))
                .andExpect(jsonPath("$.model").value("llama3.1"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.format.type").value("object"))
                .andExpect(jsonPath("$.options.num_ctx").value(16384))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("excerpt"))
                .andRespond(withSuccess("""
                        {"model":"llama3.1","message":{"role":"assistant","content":"{\\"items\\":[]}"},
                         "done":true,"done_reason":"stop","prompt_eval_count":40,"eval_count":7}""",
                        MediaType.APPLICATION_JSON));

        LlmExtractionResult result = new OllamaLlmClient(builder.build(), "llama3.1", 16384)
                .extract(new LlmExtractionRequest("rules", "excerpt", Map.of("type", "object"), null, null));

        assertEquals("{\"items\":[]}", result.text());
        assertEquals(40, result.inputTokens());
        assertEquals(7, result.outputTokens());
        server.verify();
    }

    @Test
    void connectionTestChecksThatTheModelIsPulled() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ollama.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String tags = "{\"models\":[{\"name\":\"llama3.1:latest\"}]}";
        server.expect(requestTo("http://ollama.test/api/tags")).andRespond(withSuccess(tags, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ollama.test/api/tags")).andRespond(withSuccess(tags, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ollama.test/api/chat")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"model 'mistral' not found\"}"));
        RestClient restClient = builder.build();

        assertTrue(new OllamaLlmClient(restClient, "llama3.1", 0).testConnection().ok());
        LlmDtos.LlmConnectionCheck missing = new OllamaLlmClient(restClient, "mistral", 0).testConnection();
        assertFalse(missing.ok());
        assertTrue(missing.message().contains("ollama pull mistral"));
        LlmException notFound = assertThrows(LlmException.class, () -> new OllamaLlmClient(restClient, "mistral", 0)
                .extract(new LlmExtractionRequest("rules", "excerpt", Map.of(), null, null)));
        assertEquals("Ollama does not know this model or URL (HTTP 404): model 'mistral' not found", notFound.getMessage());
    }
}
