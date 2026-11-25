package com.studytrack.api.progress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studytrack.api.support.StudyApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProgressIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private StudyApiClient api;
    private long subjectId;
    private long topicId;
    private long subtopicId;
    private long firstItemId;
    private long secondItemId;
    private long subtopicItemId;

    @BeforeEach
    void resetData() throws Exception {
        jdbcTemplate.update("DELETE FROM subjects");
        jdbcTemplate.update("DELETE FROM tags");
        jdbcTemplate.update("INSERT INTO users (id, display_name, email) SELECT 2, 'Other', 'other@study-track.local' "
                + "WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 2)");
        api = new StudyApiClient(mvc, objectMapper);
        subjectId = api.createSubject("Java");
        topicId = api.createTopic(subjectId, null, "Collections");
        subtopicId = api.createTopic(subjectId, topicId, "Maps");
        firstItemId = api.createItem(topicId, "List vs Set", "EASY", "[]");
        secondItemId = api.createItem(topicId, "ArrayList growth", "MEDIUM", "[]");
        subtopicItemId = api.createItem(subtopicId, "HashMap internals", "HARD", "[]");
    }

    @Test
    void newItemsAreNotStarted() throws Exception {
        mvc.perform(get("/api/v1/items/{id}", firstItemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.progress.reviewCount").value(0))
                .andExpect(jsonPath("$.progress.bookmarked").value(false));
    }

    @Test
    void updatesStatusConfidenceAndBookmarkPartially() throws Exception {
        patchProgress(firstItemId, "{\"status\":\"LEARNING\",\"confidence\":4}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LEARNING"))
                .andExpect(jsonPath("$.confidence").value(4));
        patchProgress(firstItemId, "{\"bookmarked\":true}")
                .andExpect(jsonPath("$.status").value("LEARNING"))
                .andExpect(jsonPath("$.confidence").value(4))
                .andExpect(jsonPath("$.bookmarked").value(true));
        patchProgress(firstItemId, "{\"confidence\":0}")
                .andExpect(jsonPath("$.confidence").doesNotExist());
        patchProgress(firstItemId, "{\"confidence\":6}").andExpect(status().isBadRequest());
        patchProgress(firstItemId, "{\"status\":\"FINISHED\"}").andExpect(status().isBadRequest());
        patchProgress(99_999, "{\"status\":\"DONE\"}").andExpect(status().isNotFound());
    }

    @Test
    void filtersTopicItemsByStatus() throws Exception {
        patchProgress(firstItemId, "{\"status\":\"DONE\"}").andExpect(status().isOk());
        mvc.perform(get("/api/v1/topics/{id}/items", topicId).param("status", "DONE"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(firstItemId))
                .andExpect(jsonPath("$.items[0].status").value("DONE"));
        mvc.perform(get("/api/v1/topics/{id}/items", topicId).param("status", "NOT_STARTED"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(secondItemId))
                .andExpect(jsonPath("$.items[0].status").value("NOT_STARTED"));
        patchProgress(secondItemId, "{\"bookmarked\":true}").andExpect(status().isOk());
        mvc.perform(get("/api/v1/topics/{id}/items", topicId).param("status", "NOT_STARTED"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].bookmarked").value(true));
        mvc.perform(get("/api/v1/topics/{id}/items", topicId).param("status", "LEARNING"))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void topicTreeAndSubjectShowProgress() throws Exception {
        patchProgress(firstItemId, "{\"status\":\"DONE\"}");
        patchProgress(subtopicItemId, "{\"status\":\"DONE\"}");
        patchProgress(secondItemId, "{\"status\":\"LEARNING\"}");
        mvc.perform(post("/api/v1/topics/{id}/complete", topicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedAt").exists());
        mvc.perform(get("/api/v1/subjects/{id}/topics", subjectId))
                .andExpect(jsonPath("$[0].doneCount").value(1))
                .andExpect(jsonPath("$[0].totalDoneCount").value(2))
                .andExpect(jsonPath("$[0].totalItemCount").value(3))
                .andExpect(jsonPath("$[0].percentDone").value(66))
                .andExpect(jsonPath("$[0].completed").value(true))
                .andExpect(jsonPath("$[0].allItemsDone").value(false))
                .andExpect(jsonPath("$[0].children[0].percentDone").value(100))
                .andExpect(jsonPath("$[0].children[0].allItemsDone").value(true))
                .andExpect(jsonPath("$[0].children[0].completed").value(false));
        mvc.perform(get("/api/v1/subjects/{id}", subjectId))
                .andExpect(jsonPath("$.doneItemCount").value(2))
                .andExpect(jsonPath("$.learningItemCount").value(1))
                .andExpect(jsonPath("$.percentDone").value(66))
                .andExpect(jsonPath("$.completedTopicCount").value(1));
        mvc.perform(delete("/api/v1/topics/{id}/complete", topicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false));
        mvc.perform(get("/api/v1/subjects/{id}/topics", subjectId))
                .andExpect(jsonPath("$[0].completed").value(false));
    }

    @Test
    void reviewsScheduleTheNextReviewAndThirdSuccessMakesDone() throws Exception {
        JsonNode first = review(firstItemId, "GOOD");
        assertEquals(3, first.get("intervalDays").asLong());
        assertEquals("LEARNING", first.get("progress").get("status").asText());
        Instant reviewedAt = Instant.parse(first.get("reviewedAt").asText());
        assertEquals(reviewedAt.plus(Duration.ofDays(3)), Instant.parse(first.get("progress").get("nextReviewAt").asText()));
        assertEquals("LEARNING", review(firstItemId, "EASY").get("progress").get("status").asText());
        JsonNode third = review(firstItemId, "GOOD");
        assertEquals("DONE", third.get("progress").get("status").asText());
        assertEquals(3, third.get("progress").get("reviewCount").asInt());
        assertEquals(6, third.get("intervalDays").asLong());
        JsonNode again = review(firstItemId, "AGAIN");
        assertEquals("LEARNING", again.get("progress").get("status").asText());
        assertEquals(1, again.get("intervalDays").asLong());
        assertEquals(4, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM review_log WHERE item_id = ?",
                Integer.class, firstItemId));

        mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + firstItemId + ",\"result\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"GOOD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dueListHasOverdueFirstThenNewItemsAndSkipsFutureAndDone() throws Exception {
        review(firstItemId, "GOOD");
        jdbcTemplate.update("UPDATE item_progress SET next_review_at = ? WHERE item_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(2))), firstItemId);
        review(secondItemId, "EASY");
        patchProgress(subtopicItemId, "{\"status\":\"DONE\"}");
        long newItemId = api.createItem(subtopicId, "TreeMap", "MEDIUM", "[]");

        mvc.perform(get("/api/v1/reviews/due").param("subjectId", String.valueOf(subjectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueCount").value(1))
                .andExpect(jsonPath("$.newCount").value(1))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(firstItemId))
                .andExpect(jsonPath("$.items[0].topicName").value("Collections"))
                .andExpect(jsonPath("$.items[0].answerHtml").exists())
                .andExpect(jsonPath("$.items[1].id").value(newItemId))
                .andExpect(jsonPath("$.items[1].status").value("NOT_STARTED"));
        mvc.perform(get("/api/v1/reviews/due").param("subjectId", String.valueOf(subjectId)).param("limit", "1"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.dueCount").value(1));
        mvc.perform(get("/api/v1/reviews/due").param("subjectId", "99999")).andExpect(status().isNotFound());
    }

    @Test
    void savesAndReadsTheResumePoint() throws Exception {
        mvc.perform(get("/api/v1/sessions/resume")).andExpect(status().isNoContent());
        saveSession("{\"subjectId\":" + subjectId + ",\"itemId\":" + subtopicItemId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicId").value(subtopicId))
                .andExpect(jsonPath("$.topicName").value("Maps"))
                .andExpect(jsonPath("$.itemTitle").value("HashMap internals"));
        long otherSubjectId = api.createSubject("Spring");
        long otherTopicId = api.createTopic(otherSubjectId, null, "Beans");
        saveSession("{\"subjectId\":" + otherSubjectId + ",\"topicId\":" + otherTopicId + "}").andExpect(status().isOk());

        mvc.perform(get("/api/v1/sessions/resume"))
                .andExpect(jsonPath("$.subjectName").value("Spring"))
                .andExpect(jsonPath("$.itemId").doesNotExist());
        mvc.perform(get("/api/v1/sessions/resume").param("subjectId", String.valueOf(subjectId)))
                .andExpect(jsonPath("$.itemId").value(subtopicItemId));

        mvc.perform(delete("/api/v1/items/{id}", subtopicItemId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/sessions/resume").param("subjectId", String.valueOf(subjectId)))
                .andExpect(jsonPath("$.topicId").value(subtopicId))
                .andExpect(jsonPath("$.itemId").doesNotExist());

        saveSession("{\"subjectId\":" + subjectId + ",\"topicId\":" + otherTopicId + "}")
                .andExpect(status().isBadRequest());
        saveSession("{\"subjectId\":" + subjectId + ",\"topicId\":" + subtopicId + ",\"itemId\":" + firstItemId + "}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherUsersRecordsAreNotFound() throws Exception {
        jdbcTemplate.update("INSERT INTO subjects (id, user_id, name) VALUES (9001, 2, 'Theirs')");
        jdbcTemplate.update("INSERT INTO topics (id, subject_id, name) VALUES (9001, 9001, 'Their topic')");
        jdbcTemplate.update("INSERT INTO study_items (id, topic_id, item_type, title) VALUES (9001, 9001, 'NOTE', 'Theirs')");
        patchProgress(9001, "{\"status\":\"DONE\"}").andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/topics/9001/complete")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":9001,\"result\":\"GOOD\"}")).andExpect(status().isNotFound());
        saveSession("{\"subjectId\":9001}").andExpect(status().isNotFound());
    }

    @Test
    void statsOverviewSumsProgressAndCountsTodaysReviews() throws Exception {
        patchProgress(subtopicItemId, "{\"status\":\"DONE\"}");
        review(firstItemId, "AGAIN");
        review(firstItemId, "HARD");
        String body = mvc.perform(get("/api/v1/stats/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.doneItems").value(1))
                .andExpect(jsonPath("$.learningItems").value(1))
                .andExpect(jsonPath("$.notStartedItems").value(1))
                .andExpect(jsonPath("$.percentDone").value(33))
                .andExpect(jsonPath("$.totalTopics").value(2))
                .andExpect(jsonPath("$.reviewsToday").value(2))
                .andExpect(jsonPath("$.currentStreakDays").value(1))
                .andExpect(jsonPath("$.newItems").value(1))
                .andExpect(jsonPath("$.subjects[0].name").value("Java"))
                .andExpect(jsonPath("$.subjects[0].newCount").value(1))
                .andExpect(jsonPath("$.reviewsPerDay.length()").value(30))
                .andReturn().getResponse().getContentAsString();
        JsonNode days = objectMapper.readTree(body).get("reviewsPerDay");
        assertEquals(2, days.get(29).get("reviewCount").asLong());
        assertTrue(days.get(0).get("date").asText().compareTo(days.get(29).get("date").asText()) < 0);
    }

    private ResultActions patchProgress(long itemId, String json) throws Exception {
        return mvc.perform(patch("/api/v1/items/{id}/progress", itemId)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions saveSession(String json) throws Exception {
        return mvc.perform(put("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private JsonNode review(long itemId, String result) throws Exception {
        String body = mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + itemId + ",\"result\":\"" + result + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
