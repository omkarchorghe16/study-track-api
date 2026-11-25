package com.studytrack.api.item;

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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ItemIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private StudyApiClient api;
    private long subjectId;
    private long topicId;

    @BeforeEach
    void resetData() throws Exception {
        jdbcTemplate.update("DELETE FROM subjects");
        jdbcTemplate.update("DELETE FROM tags");
        api = new StudyApiClient(mvc, objectMapper);
        subjectId = api.createSubject("Java");
        topicId = api.createTopic(subjectId, null, "Collections");
    }

    @Test
    void createsItemWithDerivedColumnsAndNormalisedTags() throws Exception {
        String body = mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"topicId":%d,"itemType":"QUESTION","title":"HashMap internals",
                         "questionHtml":"<p>How does a <b>HashMap</b> work?</p>",
                         "answerHtml":"<p>Buckets   and <code>hashCode</code>.</p>",
                         "tags":[" Java ","maps","java"]}
                        """.formatted(topicId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectId").value(subjectId))
                .andExpect(jsonPath("$.difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.tags[0]").value("java"))
                .andExpect(jsonPath("$.tags[1]").value("maps"))
                .andReturn().getResponse().getContentAsString();
        long itemId = api.readJson(body).get("id").asLong();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT answer_plain, content_hash FROM study_items WHERE id = ?", itemId);
        assertEquals("Buckets and hashCode.", row.get("ANSWER_PLAIN"));
        assertEquals(64, row.get("CONTENT_HASH").toString().length());
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tags", Integer.class));
    }

    @Test
    void sameQuestionWithDifferentFormattingHasSameHash() throws Exception {
        long first = api.createItem(topicId, "Same", "EASY", "[]");
        mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"topicId":%d,"itemType":"QUESTION","title":"Other title","questionHtml":"<h2>SAME?</h2>"}
                        """.formatted(topicId)))
                .andExpect(status().isCreated());
        long other = api.createItem(topicId, "Different", "EASY", "[]");
        String firstHash = hashOf(first);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM study_items WHERE content_hash = ? AND id <> ?", Integer.class, firstHash, first));
        assertNotEquals(firstHash, hashOf(other));
    }

    @Test
    void listsPagesFilteredByDifficultyAndTag() throws Exception {
        api.createItem(topicId, "One", "EASY", "[\"jvm\"]");
        api.createItem(topicId, "Two", "HARD", "[\"jvm\"]");
        api.createItem(topicId, "Three", "HARD", "[]");

        mvc.perform(get("/api/v1/topics/" + topicId + "/items?size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("One"))
                .andExpect(jsonPath("$.items[0].hasAnswer").value(true))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/topics/" + topicId + "/items?difficulty=HARD&tag=JVM"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Two"));
        mvc.perform(get("/api/v1/topics/" + topicId + "/items?difficulty=IMPOSSIBLE"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/topics/" + topicId + "/items?size=500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsPreviousAndNextInTopicOrder() throws Exception {
        long first = api.createItem(topicId, "First", "EASY", "[]");
        long second = api.createItem(topicId, "Second", "EASY", "[]");
        long third = api.createItem(topicId, "Third", "EASY", "[]");

        mvc.perform(get("/api/v1/items/" + second))
                .andExpect(jsonPath("$.previousItemId").value(first))
                .andExpect(jsonPath("$.nextItemId").value(third));
        mvc.perform(get("/api/v1/items/" + first)).andExpect(jsonPath("$.previousItemId").doesNotExist());
    }

    @Test
    void replaceMovesItemToAnotherTopicAndReplacesTags() throws Exception {
        long itemId = api.createItem(topicId, "Movable", "EASY", "[\"old\"]");
        long otherTopic = api.createTopic(subjectId, null, "Streams");
        api.createItem(otherTopic, "Existing", "EASY", "[]");

        mvc.perform(put("/api/v1/items/" + itemId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"topicId":%d,"itemType":"NOTE","title":"Moved","notesHtml":"<p>n</p>","tags":["new"]}
                        """.formatted(otherTopic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicId").value(otherTopic))
                .andExpect(jsonPath("$.itemType").value("NOTE"))
                .andExpect(jsonPath("$.answerHtml").doesNotExist())
                .andExpect(jsonPath("$.sortOrder").value(1))
                .andExpect(jsonPath("$.tags[0]").value("new"))
                .andExpect(jsonPath("$.previousItemId").exists());
    }

    @Test
    void rejectsMissingTypeAndUnknownTopic() throws Exception {
        mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":" + topicId + ",\"title\":\"No type\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.itemType").exists());
        mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":999999,\"itemType\":\"NOTE\",\"title\":\"Orphan\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesItemAndHidesOtherUsersItems() throws Exception {
        long itemId = api.createItem(topicId, "Doomed", "EASY", "[\"x\"]");
        mvc.perform(delete("/api/v1/items/" + itemId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/items/" + itemId)).andExpect(status().isNotFound());

        jdbcTemplate.update("MERGE INTO users (id, display_name, email) KEY (id) VALUES (2, 'Other', 'other@example.test')");
        jdbcTemplate.update("INSERT INTO subjects (id, user_id, name) VALUES (901, 2, 'Theirs')");
        jdbcTemplate.update("INSERT INTO topics (id, subject_id, name) VALUES (901, 901, 'Theirs')");
        jdbcTemplate.update("INSERT INTO study_items (id, topic_id, item_type, title) VALUES (901, 901, 'NOTE', 'Theirs')");
        mvc.perform(get("/api/v1/items/901")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/topics/901/items")).andExpect(status().isNotFound());
    }

    private String hashOf(long itemId) {
        return jdbcTemplate.queryForObject("SELECT content_hash FROM study_items WHERE id = ?", String.class, itemId);
    }

    @Test
    void duplicateCheckFindsSameQuestionInTheSubjectButNotTheItemItself() throws Exception {
        long otherTopic = api.createTopic(subjectId, null, "Maps");
        String created = mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"topicId":%d,"itemType":"QUESTION","title":"HashMap","questionHtml":"<p>How does a HashMap work?</p>"}
                        """.formatted(otherTopic)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long existing = api.readJson(created).get("id").asLong();
        long otherSubjectTopic = api.createTopic(api.createSubject("Python"), null, "Dicts");

        String check = """
                {"topicId":%d,"title":"Another title","questionHtml":"<h2>how does a   <b>hashmap</b> WORK?</h2>"%s}
                """;
        mvc.perform(post("/api/v1/items/duplicate-check").contentType(MediaType.APPLICATION_JSON)
                        .content(check.formatted(topicId, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(existing))
                .andExpect(jsonPath("$[0].title").value("HashMap"))
                .andExpect(jsonPath("$[0].topicName").value("Maps"));
        mvc.perform(post("/api/v1/items/duplicate-check").contentType(MediaType.APPLICATION_JSON)
                        .content(check.formatted(topicId, ",\"itemId\":" + existing)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/v1/items/duplicate-check").contentType(MediaType.APPLICATION_JSON)
                        .content(check.formatted(otherSubjectTopic, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/v1/items/duplicate-check").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":%d,\"title\":\" \"}".formatted(topicId)))
                .andExpect(status().isBadRequest());
    }
}
