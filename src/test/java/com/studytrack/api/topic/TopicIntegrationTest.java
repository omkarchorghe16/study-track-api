package com.studytrack.api.topic;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TopicIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private StudyApiClient api;
    private long subjectId;

    @BeforeEach
    void resetData() throws Exception {
        jdbcTemplate.update("DELETE FROM subjects");
        jdbcTemplate.update("DELETE FROM tags");
        api = new StudyApiClient(mvc, objectMapper);
        subjectId = api.createSubject("Spring Boot");
    }

    @Test
    void buildsTreeWithOrderedChildrenAndItemCounts() throws Exception {
        long security = api.createTopic(subjectId, null, "Security");
        long web = api.createTopic(subjectId, null, "Web");
        long jwt = api.createTopic(subjectId, security, "JWT");
        api.createTopic(subjectId, security, "OAuth2");
        api.createItem(security, "What is CSRF", "EASY", "[]");
        api.createItem(jwt, "How is a JWT signed", "MEDIUM", "[]");
        api.createItem(jwt, "Where to store a JWT", "HARD", "[]");

        mvc.perform(get("/api/v1/subjects/" + subjectId + "/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(security))
                .andExpect(jsonPath("$[0].itemCount").value(1))
                .andExpect(jsonPath("$[0].totalItemCount").value(3))
                .andExpect(jsonPath("$[0].children[0].name").value("JWT"))
                .andExpect(jsonPath("$[0].children[0].itemCount").value(2))
                .andExpect(jsonPath("$[0].children[1].name").value("OAuth2"))
                .andExpect(jsonPath("$[0].children[1].sortOrder").value(1))
                .andExpect(jsonPath("$[1].id").value(web))
                .andExpect(jsonPath("$[1].children.length()").value(0));
    }

    @Test
    void rejectsParentFromAnotherSubject() throws Exception {
        long otherSubject = api.createSubject("Other");
        long foreignTopic = api.createTopic(otherSubject, null, "Foreign");
        mvc.perform(post("/api/v1/topics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + subjectId + ",\"parentId\":" + foreignTopic + ",\"name\":\"X\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replacesNameAndDescription() throws Exception {
        long topicId = api.createTopic(subjectId, null, "Old");
        mvc.perform(put("/api/v1/topics/" + topicId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"descriptionHtml\":\"<p>About</p>\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.descriptionHtml").value("<p>About</p>"))
                .andExpect(jsonPath("$.sortOrder").value(0));
    }

    @Test
    void movesTopicButNeverUnderItsOwnSubtree() throws Exception {
        long parent = api.createTopic(subjectId, null, "Parent");
        long child = api.createTopic(subjectId, parent, "Child");
        long grandchild = api.createTopic(subjectId, child, "Grandchild");

        mvc.perform(patch("/api/v1/topics/" + parent + "/move").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":" + grandchild + ",\"sortOrder\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/topics/" + parent + "/move").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":" + parent + ",\"sortOrder\":0}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/v1/topics/" + grandchild + "/move").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":null,\"sortOrder\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andExpect(jsonPath("$.sortOrder").value(5));
        mvc.perform(get("/api/v1/subjects/" + subjectId + "/topics"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].name").value("Grandchild"));
    }

    @Test
    void deletingTopicDeletesSubtopicsAndItems() throws Exception {
        long parent = api.createTopic(subjectId, null, "Parent");
        long child = api.createTopic(subjectId, parent, "Child");
        api.createItem(child, "Question", "EASY", "[]");

        mvc.perform(delete("/api/v1/topics/" + parent)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/topics/" + child)).andExpect(status().isNotFound());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_items", Integer.class));
    }
}
