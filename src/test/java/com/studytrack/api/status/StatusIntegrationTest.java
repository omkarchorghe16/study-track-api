package com.studytrack.api.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatusIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void returnsServiceStatusWithSeededUser() throws Exception {
        mvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("study-track-api"))
                .andExpect(jsonPath("$.apiVersion").value("v1"))
                .andExpect(jsonPath("$.currentUserId").value(1))
                .andExpect(jsonPath("$.displayName").value("Default user"));
    }

    @Test
    void exposesHealthProbes() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void servesOpenApiDocument() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("StudyTrack API"))
                .andExpect(jsonPath("$.paths['/api/v1/status']").exists());
    }

    @Test
    void allowsCorsFromAngularDevServer() throws Exception {
        mvc.perform(options("/api/v1/status")
                        .header("Origin", "http://localhost:4201")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4201"));
    }

    @Test
    void returnsJsonNotFoundForUnknownRoute() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
