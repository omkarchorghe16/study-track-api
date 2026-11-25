package com.studytrack.api.media;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MediaProperties mediaProperties;
    @Autowired
    private MediaService mediaService;

    private StudyApiClient api;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM media_files");
        jdbcTemplate.update("DELETE FROM subjects");
        jdbcTemplate.update("DELETE FROM tags");
        api = new StudyApiClient(mvc, objectMapper);
    }

    @Test
    void uploadsPngAndServesItWithCachingHeaders() throws Exception {
        byte[] png = pngBytes(3, 2);
        JsonNode uploaded = upload(new MockMultipartFile("file", "../dir/Diagram.png", "application/octet-stream", png));
        long mediaId = uploaded.get("id").asLong();

        assertEquals("/api/v1/media/" + mediaId, uploaded.get("url").asText());
        assertEquals("image/png", uploaded.get("contentType").asText());
        assertEquals("Diagram.png", uploaded.get("originalName").asText());
        assertEquals(3, uploaded.get("width").asInt());
        assertEquals(2, uploaded.get("height").asInt());

        byte[] served = mvc.perform(get("/api/v1/media/" + mediaId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(png, served);
    }

    @Test
    void rejectsNonImagesOversizedFilesAndMissingParts() throws Exception {
        mvc.perform(multipart("/api/v1/media").file(new MockMultipartFile("file", "evil.png", "image/png",
                        "<svg onload=alert(1)>".getBytes())))
                .andExpect(status().isUnsupportedMediaType());
        byte[] large = new byte[(int) mediaProperties.maxImageBytes() + 1];
        System.arraycopy(pngBytes(1, 1), 0, large, 0, 8);
        mvc.perform(multipart("/api/v1/media").file(new MockMultipartFile("file", "big.png", "image/png", large)))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart("/api/v1/media").file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0])))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/media")).andExpect(status().isBadRequest());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM media_files", Integer.class));
    }

    @Test
    void deletesRowAndFile() throws Exception {
        long mediaId = upload(new MockMultipartFile("file", "a.png", "image/png", pngBytes(1, 1))).get("id").asLong();
        Path file = storedFile(mediaId);
        assertTrue(Files.exists(file));

        mvc.perform(delete("/api/v1/media/" + mediaId)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/media/" + mediaId)).andExpect(status().isNotFound());
        assertFalse(Files.exists(file));
    }

    @Test
    void hidesOtherUsersMedia() throws Exception {
        jdbcTemplate.update("MERGE INTO users (id, display_name, email) KEY (id) VALUES (2, 'Other', 'other@example.test')");
        jdbcTemplate.update("INSERT INTO media_files (id, user_id, original_name, content_type, size_bytes, storage_key)"
                + " VALUES (900, 2, 'x.png', 'image/png', 1, 'other/x.png')");
        mvc.perform(get("/api/v1/media/900")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/media/900")).andExpect(status().isNotFound());
    }

    @Test
    void itemSaveSanitisesHtmlAndLinksItsImagesAndItemDeleteRemovesThem() throws Exception {
        long mediaId = upload(new MockMultipartFile("file", "a.png", "image/png", pngBytes(1, 1))).get("id").asLong();
        long subjectId = api.createSubject("Java");
        long topicId = api.createTopic(subjectId, null, "JVM");
        long itemId = api.createItem(topicId, "Memory", "EASY", "[]");

        String answer = "<p>Heap<script>alert(1)</script></p><img src=\\\"/api/v1/media/" + mediaId
                + "\\\"><img src=\\\"https://tracker.test/p.gif\\\">";
        mvc.perform(put("/api/v1/items/" + itemId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"topicId":%d,"itemType":"QUESTION","title":"Memory","answerHtml":"%s"}
                        """.formatted(topicId, answer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerHtml").value("<p>Heap</p><img src=\"/api/v1/media/" + mediaId + "\">"));
        assertEquals(itemId, jdbcTemplate.queryForObject("SELECT item_id FROM media_files WHERE id = ?", Long.class, mediaId));

        // Removing the image from the HTML unlinks it.
        mvc.perform(put("/api/v1/items/" + itemId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"topicId":%d,"itemType":"QUESTION","title":"Memory","answerHtml":"<p>Heap</p>"}
                        """.formatted(topicId)))
                .andExpect(status().isOk());
        assertNull(jdbcTemplate.queryForObject("SELECT item_id FROM media_files WHERE id = ?", Long.class, mediaId));

        mvc.perform(put("/api/v1/items/" + itemId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"topicId":%d,"itemType":"QUESTION","title":"Memory","notesHtml":"<img src=\\"/api/v1/media/%d\\">"}
                        """.formatted(topicId, mediaId)))
                .andExpect(status().isOk());
        Path file = storedFile(mediaId);
        mvc.perform(delete("/api/v1/items/" + itemId)).andExpect(status().isNoContent());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM media_files", Integer.class));
        assertFalse(Files.exists(file));
    }

    @Test
    void cleanupDeletesOnlyOldImagesThatNothingUses() throws Exception {
        long unused = uploadPng("unused.png");
        long recent = uploadPng("recent.png");
        long shared = uploadPng("shared.png");
        long inTopic = uploadPng("topic.png");
        long subjectId = api.createSubject("Java");
        long topicId = api.createTopic(subjectId, null, "JVM");
        long first = api.createItem(topicId, "First", "EASY", "[]");
        long second = api.createItem(topicId, "Second", "EASY", "[]");
        // The shared image is linked to the first item only; the second item uses it too.
        replaceAnswer(topicId, first, "First", "<img src=\\\"/api/v1/media/" + shared + "\\\">");
        replaceAnswer(topicId, second, "Second", "<p>Also</p><img src=\\\"/api/v1/media/" + shared + "\\\">");
        replaceAnswer(topicId, first, "First", "<p>No image now</p>");
        mvc.perform(put("/api/v1/topics/" + topicId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"JVM","descriptionHtml":"<img src=\\"/api/v1/media/%d\\">"}
                        """.formatted(inTopic)))
                .andExpect(status().isOk());
        assertNull(jdbcTemplate.queryForObject("SELECT item_id FROM media_files WHERE id = ?", Long.class, shared));
        jdbcTemplate.update("UPDATE media_files SET created_at = DATEADD('DAY', -30, CURRENT_TIMESTAMP) WHERE id <> ?", recent);
        Path unusedFile = storedFile(unused);

        int deleted = mediaService.deleteUnusedMedia(Instant.now().minus(Duration.ofDays(7)));

        assertEquals(1, deleted);
        assertEquals(List.of(recent, shared, inTopic),
                jdbcTemplate.queryForList("SELECT id FROM media_files ORDER BY id", Long.class));
        assertFalse(Files.exists(unusedFile));
    }

    private long uploadPng(String name) throws Exception {
        return upload(new MockMultipartFile("file", name, "image/png", pngBytes(1, 1))).get("id").asLong();
    }

    private void replaceAnswer(long topicId, long itemId, String title, String answerJson) throws Exception {
        mvc.perform(put("/api/v1/items/" + itemId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"topicId":%d,"itemType":"QUESTION","title":"%s","answerHtml":"%s"}
                        """.formatted(topicId, title, answerJson)))
                .andExpect(status().isOk());
    }

    private JsonNode upload(MockMultipartFile file) throws Exception {
        String body = mvc.perform(multipart("/api/v1/media").file(file))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private Path storedFile(long mediaId) {
        String storageKey = jdbcTemplate.queryForObject("SELECT storage_key FROM media_files WHERE id = ?",
                String.class, mediaId);
        return Path.of(mediaProperties.storageDir()).toAbsolutePath().normalize().resolve(storageKey);
    }

    private static byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
