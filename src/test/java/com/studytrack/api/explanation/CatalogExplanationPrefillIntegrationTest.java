package com.studytrack.api.explanation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studytrack.api.llm.FakeLlmClientFactory;
import com.studytrack.api.llm.LlmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogExplanationPrefillIntegrationTest {
    private static final String EXPLANATION_JSON = """
            {"summary":"Short answer.","analogy":"","keyPoints":["One"],"howItWorks":[],
             "example":{"title":"","language":"","code":"","explanation":""},
             "realWorldExample":{"title":"Cart","scenario":"A cart.","howItApplies":"Fast."},
             "commonMistakes":[],"interviewTip":""}""";
    private static final String CATALOG_CSV = """
            track,subject,topic_path,question,answer,difficulty
            Java,Core Java,Collections > HashMap,How does a HashMap work?,A hash table.,MEDIUM
            Java,Core Java,Collections > HashMap,Why override `hashCode` with equals?,Keeps the contract.,MEDIUM
            Java,Core Java,Multithreading,What is a deadlock?,Threads wait forever.,HARD
            """;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FakeLlmClientFactory fakeLlm;

    @BeforeEach
    void resetData() throws Exception {
        jdbcTemplate.update("DELETE FROM study_track.llm_settings");
        jdbcTemplate.update("DELETE FROM study_track.subjects");
        jdbcTemplate.update("DELETE FROM study_track.catalog_tracks");
        jdbcTemplate.update("DELETE FROM study_track.shared_explanations");
        jdbcTemplate.update("UPDATE study_track.users SET is_admin = TRUE WHERE id = 1");
        fakeLlm.reset();
        fakeLlm.answerWith(request -> EXPLANATION_JSON);
        mvc.perform(multipart("/api/v1/admin/catalog/import")
                        .file(new MockMultipartFile("file", "c.csv", "text/csv", CATALOG_CSV.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionsAdded").value(3));
    }

    @Test
    void prefilledExplanationsAreFoundByUsersWhoAddTheSubjectWithoutCallingTheAi() throws Exception {
        mvc.perform(get("/api/v1/admin/catalog/explanations/status"))
                .andExpect(jsonPath("$.missing").value(3))
                .andExpect(jsonPath("$.questions").value(3));
        mvc.perform(post("/api/v1/admin/catalog/explanations/prefill"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.total").value(3));
        JsonNode done = waitUntilFinished();
        assertEquals("COMPLETED", done.get("state").asText());
        assertEquals(3, done.get("completed").asInt());
        assertEquals(0, done.get("missing").asInt());
        assertEquals(3, fakeLlm.getRequests().size());

        long coreJavaId = findCoreJavaCatalogId();
        String subject = mvc.perform(post("/api/v1/catalog/subjects/" + coreJavaId + "/enroll"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long subjectId = objectMapper.readTree(subject).get("id").asLong();
        String items = mvc.perform(get("/api/v1/subjects/" + subjectId + "/items"))
                .andReturn().getResponse().getContentAsString();
        JsonNode list = objectMapper.readTree(items).get("items");
        assertEquals(3, list.size());
        for (JsonNode item : list) {
            mvc.perform(get("/api/v1/items/" + item.get("id").asLong() + "/explanation"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.summary").value("Short answer."));
            mvc.perform(post("/api/v1/items/" + item.get("id").asLong() + "/explanation")).andExpect(status().isOk());
        }
        assertEquals(3, fakeLlm.getRequests().size(), "opening the copied questions must not call the AI");
    }

    @Test
    void aSecondRunOnlyWritesWhatIsStillMissing() throws Exception {
        mvc.perform(post("/api/v1/admin/catalog/explanations/prefill")).andExpect(status().isAccepted());
        waitUntilFinished();
        jdbcTemplate.update("DELETE FROM study_track.shared_explanations WHERE id = (SELECT MIN(id) FROM study_track.shared_explanations)");
        fakeLlm.reset();
        fakeLlm.answerWith(request -> EXPLANATION_JSON);

        mvc.perform(post("/api/v1/admin/catalog/explanations/prefill"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.total").value(1));
        waitUntilFinished();
        assertEquals(1, fakeLlm.getRequests().size());

        mvc.perform(post("/api/v1/admin/catalog/explanations/prefill"))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.state").value("COMPLETED"));
        assertEquals(1, fakeLlm.getRequests().size());
    }

    @Test
    void stopsAfterFailuresInARowBeforeTryingTheRestAndContinuesOnTheNextRun() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        fakeLlm.answerWith(request -> {
            calls.incrementAndGet();
            throw new LlmException(LlmException.Reason.UNAVAILABLE, "Rate limit reached");
        });
        mvc.perform(post("/api/v1/admin/catalog/explanations/prefill")).andExpect(status().isAccepted());
        JsonNode stopped = waitUntilFinished();
        assertEquals("FAILED", stopped.get("state").asText());
        assertEquals(0, stopped.get("completed").asInt());
        assertEquals(2, stopped.get("failed").asInt());
        assertEquals(3, stopped.get("missing").asInt());
        assertEquals(2, calls.get(), "the third question must not be tried after two failures in a row");
        assertTrue(stopped.get("message").asText().contains("failures in a row"), stopped.get("message").asText());

        fakeLlm.answerWith(request -> EXPLANATION_JSON);
        mvc.perform(post("/api/v1/admin/catalog/explanations/prefill"))
                .andExpect(jsonPath("$.total").value(3));
        JsonNode finished = waitUntilFinished();
        assertEquals("COMPLETED", finished.get("state").asText());
        assertEquals(0, finished.get("missing").asInt());
    }

    @Test
    void stopsAtOnceWhenTheKeyIsMissingOrAiIsOff() throws Exception {
        fakeLlm.failClientCreation(new LlmException(LlmException.Reason.CONFIGURATION, "GROQ_API_KEY is not set"));
        mvc.perform(post("/api/v1/admin/catalog/explanations/prefill")).andExpect(status().isAccepted());
        JsonNode stopped = waitUntilFinished();
        assertEquals("FAILED", stopped.get("state").asText());
        assertEquals("GROQ_API_KEY is not set", stopped.get("message").asText());
        assertEquals(0, stopped.get("completed").asInt());
        assertEquals(0, stopped.get("failed").asInt());
    }

    @Test
    void onlyAnAdminCanStartOrFollowThePrefill() throws Exception {
        jdbcTemplate.update("UPDATE study_track.users SET is_admin = FALSE WHERE id = 1");
        mvc.perform(post("/api/v1/admin/catalog/explanations/prefill")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/catalog/explanations/status")).andExpect(status().isForbidden());
        assertEquals(0, fakeLlm.getRequests().size());
    }

    private long findCoreJavaCatalogId() throws Exception {
        String tracks = mvc.perform(get("/api/v1/catalog/tracks")).andReturn().getResponse().getContentAsString();
        long trackId = objectMapper.readTree(tracks).get(0).get("id").asLong();
        String subjects = mvc.perform(get("/api/v1/catalog/tracks/" + trackId + "/subjects"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(subjects).get(0).get("id").asLong();
    }

    private JsonNode waitUntilFinished() throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            String body = mvc.perform(get("/api/v1/admin/catalog/explanations/status"))
                    .andReturn().getResponse().getContentAsString();
            JsonNode node = objectMapper.readTree(body);
            if (!"RUNNING".equals(node.get("state").asText())) {
                return node;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The pre-fill did not finish");
    }
}
