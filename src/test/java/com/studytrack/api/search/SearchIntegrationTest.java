package com.studytrack.api.search;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs on H2, so it covers the LIKE fallback of ItemSearchRepository (PostgreSQL uses search_vector). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ItemSearchRepository itemSearchRepository;

    private long javaSubject;
    private long hashMapItem;

    @BeforeEach
    void createItems() throws Exception {
        jdbcTemplate.update("DELETE FROM subjects");
        jdbcTemplate.update("DELETE FROM tags");
        StudyApiClient api = new StudyApiClient(mvc, objectMapper);
        javaSubject = api.createSubject("Java");
        long collections = api.createTopic(javaSubject, null, "Collections");
        hashMapItem = createItem(collections, "HashMap internals", "<p>How does a HashMap store entries?</p>",
                "<p>An array of buckets; each bucket holds a linked list or a tree.</p>");
        createItem(collections, "ArrayList growth", "<p>How does an ArrayList grow?</p>",
                "<p>It copies into an array about 1.5 times larger, unlike a hash table.</p>");
        long pythonTopic = api.createTopic(api.createSubject("Python"), null, "Dicts");
        createItem(pythonTopic, "Dict lookup", "<p>How is a dict implemented?</p>", "<p>As a hash table with open addressing.</p>");
    }

    @Test
    void findsItemsByWordPrefixWithLocationStatusAndSnippet() throws Exception {
        mvc.perform(patch("/api/v1/items/" + hashMapItem + "/progress").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LEARNING\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/items/search").param("q", "  bucket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("bucket"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].itemId").value(hashMapItem))
                .andExpect(jsonPath("$.items[0].subjectName").value("Java"))
                .andExpect(jsonPath("$.items[0].topicName").value("Collections"))
                .andExpect(jsonPath("$.items[0].status").value("LEARNING"))
                .andExpect(jsonPath("$.items[0].itemType").value("QUESTION"))
                .andExpect(jsonPath("$.items[0].snippet[0].text").value("An array of "))
                .andExpect(jsonPath("$.items[0].snippet[0].match").value(false))
                .andExpect(jsonPath("$.items[0].snippet[1].text").value("buckets"))
                .andExpect(jsonPath("$.items[0].snippet[1].match").value(true));
    }

    @Test
    void requiresEveryWordAndRanksTitleMatchesFirst() throws Exception {
        mvc.perform(get("/api/v1/items/search").param("q", "hash"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].itemId").value(hashMapItem));
        mvc.perform(get("/api/v1/items/search").param("q", "hash \"open\" OR"))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/items/search").param("q", "hash open"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Dict lookup"));
    }

    @Test
    void filtersBySubjectAndPages() throws Exception {
        mvc.perform(get("/api/v1/items/search").param("q", "hash").param("subjectId", String.valueOf(javaSubject))
                        .param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("ArrayList growth"));
        mvc.perform(get("/api/v1/items/search").param("q", "hash").param("subjectId", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void queryWithoutWordsFindsNothingAndBlankQueryIsRejected() throws Exception {
        mvc.perform(get("/api/v1/items/search").param("q", "%_*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/items/search").param("q", " ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/items/search")).andExpect(status().isBadRequest());
        assertFalse(itemSearchRepository.isFullTextSearch());
    }

    private long createItem(long topicId, String title, String questionHtml, String answerHtml) throws Exception {
        String body = mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(java.util.Map.of("topicId", topicId, "itemType", "QUESTION",
                                "title", title, "questionHtml", questionHtml, "answerHtml", answerHtml))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }
}
