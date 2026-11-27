package com.studytrack.api.explanation;

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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
        jdbcTemplate.update("DELETE FROM study_track.shared_explanations");
        jdbcTemplate.update("UPDATE study_track.users SET is_admin = TRUE WHERE id = 1");
        fakeLlm.reset();
        fakeLlm.answerWith(request -> EXPLANATION_JSON);
        api = new StudyApiClient(mvc, objectMapper);
        subjectId = api.createSubject("Java");
        topicId = api.createTopic(subjectId, null, "Collections");
    }

    @Test
    void getNeverCallsTheAiAndPostWritesOnceThenServesTheSavedOne() throws Exception {
        long questionId = api.createItem(topicId, "How does a HashMap work", "MEDIUM", "[]");
        mvc.perform(get("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isNotFound());
        assertEquals(0, fakeLlm.getRequests().size());

        mvc.perform(post("/api/v1/items/" + questionId + "/explanation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value(questionId))
                .andExpect(jsonPath("$.content.summary").value("A HashMap stores key-value pairs in buckets."))
                .andExpect(jsonPath("$.content.keyPoints.length()").value(2))
                .andExpect(jsonPath("$.content.howItWorks.length()").value(1))
                .andExpect(jsonPath("$.content.example.language").value("java"))
                .andExpect(jsonPath("$.content.realWorldExample.title").value("Shopping cart"))
                .andExpect(jsonPath("$.model").exists());
        mvc.perform(get("/api/v1/items/" + questionId + "/explanation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.commonMistakes[0]").value("Mutable keys"));
        mvc.perform(post("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isOk());

        assertEquals(1, fakeLlm.getRequests().size());
        String prompt = fakeLlm.getRequests().getFirst().userPrompt();
        assertTrue(prompt.contains("Subject: Java") && prompt.contains("Topic: Collections")
                && prompt.contains("How does a HashMap work"), prompt);
        assertFalse(prompt.contains("Answer"), "the learner's own answer must not be sent: " + prompt);
    }

    @Test
    void sameQuestionInTheSameSubjectSharesOneExplanationEvenAfterTheFirstCopyIsDeleted() throws Exception {
        long second = api.createTopic(subjectId, null, "Maps");
        long first = api.createItem(topicId, "What is a HashMap", "EASY", "[]");
        long copy = api.createItem(second, "What is a HashMap", "EASY", "[]");
        mvc.perform(post("/api/v1/items/" + first + "/explanation")).andExpect(status().isOk());

        mvc.perform(get("/api/v1/items/" + copy + "/explanation")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/items/" + first)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/items/" + copy)).andExpect(status().isNoContent());
        long again = api.createItem(topicId, "What is a HashMap", "EASY", "[]");
        mvc.perform(post("/api/v1/items/" + again + "/explanation")).andExpect(status().isOk());

        assertEquals(1, fakeLlm.getRequests().size());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_track.shared_explanations", Integer.class));
    }

    @Test
    void theSameQuestionInAnotherSubjectGetsItsOwnExplanation() throws Exception {
        long javaQuestion = api.createItem(topicId, "What is polymorphism", "EASY", "[]");
        long pythonSubject = api.createSubject("Python");
        long pythonQuestion = api.createItem(api.createTopic(pythonSubject, null, "OOP"), "What is polymorphism", "EASY", "[]");
        mvc.perform(post("/api/v1/items/" + javaQuestion + "/explanation")).andExpect(status().isOk());

        mvc.perform(get("/api/v1/items/" + pythonQuestion + "/explanation")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/items/" + pythonQuestion + "/explanation")).andExpect(status().isOk());
        assertEquals(2, fakeLlm.getRequests().size());
    }

    @Test
    void twoUsersOpeningTheSameNewQuestionAtOnceCauseOneAiCall() throws Exception {
        long first = api.createItem(topicId, "What is a deadlock", "EASY", "[]");
        long copy = api.createItem(api.createTopic(subjectId, null, "Threads"), "What is a deadlock", "EASY", "[]");
        AtomicInteger calls = new AtomicInteger();
        fakeLlm.answerWith(request -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(200);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return EXPLANATION_JSON;
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = List.of(
                    pool.submit(() -> mvc.perform(post("/api/v1/items/" + first + "/explanation")).andReturn().getResponse().getStatus()),
                    pool.submit(() -> mvc.perform(post("/api/v1/items/" + copy + "/explanation")).andReturn().getResponse().getStatus()));
            for (Future<Integer> result : results) {
                assertEquals(200, result.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, calls.get());
    }

    @Test
    void asksOnceMoreWhenTheFirstReplyIsNotJsonAndFailsAfterTwoBadRepliesWithoutSavingAnything() throws Exception {
        long questionId = api.createItem(topicId, "What is a HashMap", "EASY", "[]");
        AtomicInteger calls = new AtomicInteger();
        fakeLlm.answerWith(request -> calls.incrementAndGet() == 1 ? "not json" : EXPLANATION_JSON);
        mvc.perform(post("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isOk());
        assertEquals(2, calls.get());
        assertTrue(fakeLlm.getRequests().get(1).isRetry());

        jdbcTemplate.update("DELETE FROM study_track.shared_explanations");
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
        assertEquals(0, fakeLlm.getRequests().size());
        mvc.perform(post("/api/v1/items/999999/explanation")).andExpect(status().isNotFound());
    }

    @Test
    void savedExplanationIsServedEvenWhenAiIsTurnedOff() throws Exception {
        long questionId = api.createItem(topicId, "What is a HashMap", "EASY", "[]");
        mvc.perform(post("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isOk());
        jdbcTemplate.update("INSERT INTO study_track.llm_settings (user_id, provider, model, enabled) VALUES (1, 'GROQ', 'm', FALSE)");

        mvc.perform(post("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isOk());
        assertEquals(1, fakeLlm.getRequests().size());
    }

    @Test
    void onlyAnAdminCanRemoveASavedExplanationAndTheNextPostWritesAgain() throws Exception {
        long questionId = api.createItem(topicId, "What is a HashMap", "EASY", "[]");
        mvc.perform(post("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isOk());

        jdbcTemplate.update("UPDATE study_track.users SET is_admin = FALSE WHERE id = 1");
        mvc.perform(delete("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isOk());

        jdbcTemplate.update("UPDATE study_track.users SET is_admin = TRUE WHERE id = 1");
        mvc.perform(delete("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/items/" + questionId + "/explanation")).andExpect(status().isOk());
        assertEquals(2, fakeLlm.getRequests().size());
        mvc.perform(delete("/api/v1/items/999999/explanation")).andExpect(status().isNotFound());
    }
}
