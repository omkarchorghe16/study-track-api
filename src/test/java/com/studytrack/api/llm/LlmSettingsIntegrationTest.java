package com.studytrack.api.llm;

import com.studytrack.api.llm.LlmDtos.LlmConnectionCheck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LlmSettingsIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FakeLlmClientFactory fakeLlm;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM llm_settings");
        fakeLlm.reset();
    }

    @Test
    void returnsDefaultsAndProviderKeyStatusWithoutTheKey() throws Exception {
        mvc.perform(get("/api/v1/settings/llm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("ANTHROPIC"))
                .andExpect(jsonPath("$.model").value("claude-opus-5-5"))
                .andExpect(jsonPath("$.baseUrl").value("https://api.anthropic.com"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.providers.length()").value(3))
                .andExpect(jsonPath("$.providers[0].provider").value("ANTHROPIC"))
                .andExpect(jsonPath("$.providers[0].apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.providers[1].apiKeyConfigured").value(false))
                .andExpect(jsonPath("$.providers[2].apiKeyRequired").value(false))
                .andExpect(content().string(not(containsString("test-key-not-real"))));
    }

    @Test
    void savesSettingsWithANormalisedBaseUrl() throws Exception {
        mvc.perform(put("/api/v1/settings/llm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OLLAMA\",\"model\":\" llama3.1 \",\"baseUrl\":\"http://localhost:11434/\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("OLLAMA"))
                .andExpect(jsonPath("$.model").value("llama3.1"))
                .andExpect(jsonPath("$.baseUrl").value("http://localhost:11434"))
                .andExpect(jsonPath("$.saved").value(true));

        mvc.perform(put("/api/v1/settings/llm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENAI\",\"model\":\"gpt-4o-mini\",\"baseUrl\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseUrl").value("https://api.openai.com"));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM llm_settings", Integer.class));
    }

    @Test
    void rejectsUnsafeBaseUrlsAndMissingFields() throws Exception {
        for (String baseUrl : new String[]{"ftp://host", "http://user:secret@host", "https://host/v1?key=1", "not a url"}) {
            mvc.perform(put("/api/v1/settings/llm").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"provider\":\"OLLAMA\",\"model\":\"m\",\"baseUrl\":\"" + baseUrl + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/v1/settings/llm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GEMINI\",\"model\":\"m\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/settings/llm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OLLAMA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testsUnsavedOrSavedSettings() throws Exception {
        mvc.perform(post("/api/v1/settings/llm/test").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OLLAMA\",\"model\":\"llama3.1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.provider").value("OLLAMA"));
        assertEquals("http://localhost:11434", fakeLlm.getCreatedFor().getLast().baseUrl());

        fakeLlm.answerConnectionTest(new LlmConnectionCheck(false, "Anthropic rejected the API key (HTTP 401)"));
        mvc.perform(post("/api/v1/settings/llm/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.provider").value("ANTHROPIC"))
                .andExpect(jsonPath("$.message").value("Anthropic rejected the API key (HTTP 401)"));

        fakeLlm.failClientCreation(new LlmException(LlmException.Reason.CONFIGURATION, "OPENAI_API_KEY is not set on the server"));
        mvc.perform(post("/api/v1/settings/llm/test").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENAI\",\"model\":\"gpt-4o-mini\"}"))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value("OPENAI_API_KEY is not set on the server"));
    }
}
