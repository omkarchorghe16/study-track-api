package com.studytrack.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Creates subjects, topics and items through the real endpoints for integration tests. */
public class StudyApiClient {
    private final MockMvc mvc;
    private final ObjectMapper objectMapper;

    public StudyApiClient(MockMvc mvc, ObjectMapper objectMapper) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
    }

    public long createSubject(String name) throws Exception {
        return postForId("/api/v1/subjects", "{\"name\":\"" + name + "\"}");
    }

    public long createTopic(long subjectId, Long parentId, String name) throws Exception {
        String parent = parentId == null ? "null" : parentId.toString();
        return postForId("/api/v1/topics",
                "{\"subjectId\":" + subjectId + ",\"parentId\":" + parent + ",\"name\":\"" + name + "\"}");
    }

    public long createItem(long topicId, String title, String difficulty, String tagsJson) throws Exception {
        return postForId("/api/v1/items", """
                {"topicId":%d,"itemType":"QUESTION","title":"%s","questionHtml":"<p>%s?</p>",
                 "answerHtml":"<p><b>Answer</b> to %s</p>","difficulty":"%s","tags":%s}
                """.formatted(topicId, title, title, title, difficulty, tagsJson));
    }

    public JsonNode readJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private long postForId(String path, String json) throws Exception {
        String body = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }
}
