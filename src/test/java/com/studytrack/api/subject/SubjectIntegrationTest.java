package com.studytrack.api.subject;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubjectIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private StudyApiClient api;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM subjects");
        jdbcTemplate.update("DELETE FROM tags");
        jdbcTemplate.update("DELETE FROM users WHERE id <> 1");
        api = new StudyApiClient(mvc, objectMapper);
    }

    @Test
    void createsSubjectWithLocationAndListsItWithCounts() throws Exception {
        mvc.perform(post("/api/v1/subjects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Java Interview \",\"description\":\"Core Java\",\"icon\":\"☕\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/subjects/")))
                .andExpect(jsonPath("$.name").value("Java Interview"))
                .andExpect(jsonPath("$.sortOrder").value(0))
                .andExpect(jsonPath("$.archived").value(false));
        long subjectId = api.createSubject("Spring Boot");
        long topicId = api.createTopic(subjectId, null, "Security");
        api.createTopic(subjectId, topicId, "JWT");
        api.createItem(topicId, "What is CSRF", "EASY", "[]");

        mvc.perform(get("/api/v1/subjects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Java Interview"))
                .andExpect(jsonPath("$[0].topicCount").value(0))
                .andExpect(jsonPath("$[1].name").value("Spring Boot"))
                .andExpect(jsonPath("$[1].sortOrder").value(1))
                .andExpect(jsonPath("$[1].topicCount").value(2))
                .andExpect(jsonPath("$[1].itemCount").value(1));
    }

    @Test
    void rejectsBlankNameAndDuplicateName() throws Exception {
        mvc.perform(post("/api/v1/subjects").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists());
        api.createSubject("Java");
        mvc.perform(post("/api/v1/subjects").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"java\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void replacesSubjectAndKeepsSortOrderWhenOmitted() throws Exception {
        api.createSubject("First");
        long subjectId = api.createSubject("Second");
        mvc.perform(put("/api/v1/subjects/" + subjectId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"archived\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.sortOrder").value(1));
    }

    @Test
    void deletesSubjectWithItsTopicsAndItems() throws Exception {
        long subjectId = api.createSubject("Temporary");
        long topicId = api.createTopic(subjectId, null, "Topic");
        api.createItem(topicId, "Question", "HARD", "[\"temp\"]");

        mvc.perform(delete("/api/v1/subjects/" + subjectId)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/subjects/" + subjectId)).andExpect(status().isNotFound());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM topics", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_items", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_item_tags", Integer.class));
    }

    @Test
    void hidesOtherUsersSubjects() throws Exception {
        jdbcTemplate.update("INSERT INTO users (id, display_name, email) VALUES (2, 'Other', 'other@example.test')");
        jdbcTemplate.update("INSERT INTO subjects (id, user_id, name) VALUES (900, 2, 'Not mine')");

        mvc.perform(get("/api/v1/subjects")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/subjects/900")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/subjects/900")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/subjects/900/topics")).andExpect(status().isNotFound());
    }
}
