package com.studytrack.api.explanation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studytrack.api.llm.FakeLlmClientFactory;
import com.studytrack.api.llm.LlmException;
import com.studytrack.api.support.StudyApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExplanationIntegrationTest {
    private static final String EXPLANATION_JSON = """
            {"summary":"A HashMap stores key-value pairs in buckets.","analogy":"Like a post office with numbered boxes.",
             "keyPoints":["Average O(1) lookup","Needs equals and hashCode",""],
             "howItWorks":[{"title":"Hash","detail":"The key's hashCode picks a bucket."},{"title":"Compare","detail":""}],
             "example":{"title":"Count words","language":"Java","code":"Map<String,Integer> m = new HashMap<>();","explanation":"Counts words."},
             "realWorldExample":{"title":"Shopping cart","scenario":"A cart keeps quantities by product id.","howItApplies":"Lookups by id are fast."},
             "commonMistakes":["Mutable keys"],"interviewTip":"Mention load factor."}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FakeLlmClientFactory fakeLlm;

    private StudyApiClient api;
    private long subjectId;
    private long topicId;

    @BeforeEach
    void resetData() throws Exception {
        jdbcTemplate.update("DELETE FROM study_track.llm_settings");
        jdbcTemplate.update("DELETE FROM study_track.subjects");
        fakeLlm.reset();
        fakeLlm.answerWith(request -> EXPLANATION_JSON);
        api = new StudyApiClient(mvc, objectMapper);
        subjectId = api.createSubject("Java");
        topicId = api.createTopic(subjectId, null, "Collections");
    }

    @Test
    void generatesStoresAndReadsAnExplanationAndCleansTheModelsText() throws Exception {
        long questionId = api.createItem(topicId, "How does a HashMap work", "MEDIUM", "[]");
        mvc.perform(get("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/items/" + questionId + "/explanation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value(questionId))
                .andExpect(jsonPath("$.content.summary").value("A HashMap stores key-value pairs in buckets."))
                .andExpect(jsonPath("$.content.keyPoints.length()").value(2))
                .andExpect(jsonPath("$.content.howItWorks.length()").value(1))
                .andExpect(jsonPath("$.content.example.language").value("java"))
                .andExpect(jsonPath("$.content.realWorldExample.title").value("Shopping cart"))
                .andExpect(jsonPath("$.provider").exists())
                .andExpect(jsonPath("$.model").exists());
        mvc.perform(get("/api/v1/items/" + questionId + "/explanation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.commonMistakes[0]").value("Mutable keys"));
        assertEquals(1, fakeLlm.getRequests().size());
        String prompt = fakeLlm.getRequests().getFirst().userPrompt();
        assertTrue(prompt.contains("Subject: Java") && prompt.contains("Topic: Collections"), prompt);
    }

    @Test
    void generatingAgainReplacesTheExplanationInsteadOfAddingOne() throws Exception {
        long questionId = api.createItem(topicId, "What is a HashMap", "EASY", "[]");
        mvc.perform(post("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isOk());
        fakeLlm.answerWith(request -> EXPLANATION_JSON.replace("stores key-value pairs", "maps keys to values"));

        mvc.perform(post("/api/v1/items/" + questionId + "/explanation"))
                .andExpect(jsonPath("$.content.summary").value("A HashMap maps keys to values in buckets."));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_track.question_explanations", Integer.class));
    }

    @Test
    void asksOnceMoreWhenTheFirstReplyIsNotJsonAndFailsAfterTwoBadReplies() throws Exception {
        long questionId = api.createItem(topicId, "What is a HashMap", "EASY", "[]");
        AtomicInteger calls = new AtomicInteger();
        fakeLlm.answerWith(request -> calls.incrementAndGet() == 1 ? "not json" : EXPLANATION_JSON);
        mvc.perform(post("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isOk());
        assertEquals(2, calls.get());
        assertTrue(fakeLlm.getRequests().get(1).isRetry());

        jdbcTemplate.update("DELETE FROM study_track.question_explanations");
        fakeLlm.answerWith(request -> "{\"summary\":\"\"}");
        mvc.perform(post("/api/v1/items/" + questionId + "/explanation"))
                .andExpect(status().isFailedDependency())
                .andExpect(jsonPath("$.message").value("The AI did not return a usable explanation for this question"));
        mvc.perform(get("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isNotFound());
    }

    @Test
    void reportsProviderProblemsAndAiBeingOffWithMessagesTheAppCanShow() throws Exception {
        long questionId = api.createItem(topicId, "What is a HashMap", "EASY", "[]");
        fakeLlm.answerWith(request -> {
            throw new LlmException(LlmException.Reason.UNAVAILABLE, "Groq is busy, try again later");
        });
        mvc.perform(post("/api/v1/items/" + questionId + "/explanation"))
                .andExpect(status().isFailedDependency())
                .andExpect(jsonPath("$.message").value("Groq is busy, try again later"));

        fakeLlm.failClientCreation(new LlmException(LlmException.Reason.CONFIGURATION, "GROQ_API_KEY is not set"));
        mvc.perform(post("/api/v1/items/" + questionId + "/explanation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("GROQ_API_KEY is not set"));

        fakeLlm.reset();
        jdbcTemplate.update("INSERT INTO study_track.llm_settings (user_id, provider, model, enabled) VALUES (1, 'GROQ', 'm', FALSE)");
        mvc.perform(post("/api/v1/items/" + questionId + "/explanation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("AI is turned off in Settings"));
        mvc.perform(post("/api/v1/items/999999/explanation")).andExpect(status().isNotFound());
    }

    @Test
    void explainsAllMissingQuestionsOfASubjectInTheBackground() throws Exception {
        long first = api.createItem(topicId, "First question", "EASY", "[]");
        api.createItem(topicId, "Second question", "EASY", "[]");
        api.createItem(topicId, "Third question", "EASY", "[]");
        mvc.perform(post("/api/v1/items/" + first + "/explanation")).andExpect(status().isOk());
        fakeLlm.reset();
        fakeLlm.answerWith(request -> EXPLANATION_JSON);

        mvc.perform(get("/api/v1/subjects/" + subjectId + "/explanations/status"))
                .andExpect(jsonPath("$.state").value("IDLE"))
                .andExpect(jsonPath("$.missing").value(2));
        mvc.perform(post("/api/v1/subjects/" + subjectId + "/explanations"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.total").value(2));

        JsonNode statusBody = waitUntilFinished();
        assertEquals("COMPLETED", statusBody.get("state").asText());
        assertEquals(2, statusBody.get("completed").asInt());
        assertEquals(0, statusBody.get("failed").asInt());
        assertEquals(0, statusBody.get("missing").asInt());
        assertEquals(2, fakeLlm.getRequests().size());
        mvc.perform(post("/api/v1/subjects/" + subjectId + "/explanations"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.state").value("COMPLETED"));
        mvc.perform(post("/api/v1/subjects/999999/explanations")).andExpect(status().isNotFound());
    }

    @Test
    void backgroundRunCountsFailuresAndStopsWhenTheKeyIsMissing() throws Exception {
        api.createItem(topicId, "First question", "EASY", "[]");
        api.createItem(topicId, "Second question", "EASY", "[]");
        AtomicInteger calls = new AtomicInteger();
        fakeLlm.answerWith(request -> {
            if (calls.incrementAndGet() == 1) {
                throw new LlmException(LlmException.Reason.REJECTED, "Rejected");
            }
            return EXPLANATION_JSON;
        });
        mvc.perform(post("/api/v1/subjects/" + subjectId + "/explanations")).andExpect(status().isAccepted());
        JsonNode mixed = waitUntilFinished();
        assertEquals(1, mixed.get("completed").asInt());
        assertEquals(1, mixed.get("failed").asInt());

        long topic2 = api.createTopic(subjectId, null, "More");
        api.createItem(topic2, "Third question", "EASY", "[]");
        fakeLlm.failClientCreation(new LlmException(LlmException.Reason.CONFIGURATION, "GROQ_API_KEY is not set"));
        mvc.perform(post("/api/v1/subjects/" + subjectId + "/explanations")).andExpect(status().isAccepted());
        JsonNode stopped = waitUntilFinished();
        assertEquals("FAILED", stopped.get("state").asText());
        assertEquals("GROQ_API_KEY is not set", stopped.get("message").asText());
    }

    private JsonNode waitUntilFinished() throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            String body = mvc.perform(get("/api/v1/subjects/" + subjectId + "/explanations/status"))
                    .andReturn().getResponse().getContentAsString();
            JsonNode node = objectMapper.readTree(body);
            if (!"RUNNING".equals(node.get("state").asText())) {
                return node;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The background run did not finish");
    }
}
