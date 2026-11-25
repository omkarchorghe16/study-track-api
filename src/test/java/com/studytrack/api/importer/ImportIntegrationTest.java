package com.studytrack.api.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studytrack.api.llm.FakeLlmClientFactory;
import com.studytrack.api.llm.LlmDtos.LlmExtractionRequest;
import com.studytrack.api.llm.LlmException;
import com.studytrack.api.support.StudyApiClient;
import com.studytrack.api.support.TestDocuments;
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
import org.springframework.test.web.servlet.ResultMatcher;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportIntegrationTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\[\\[IMG:(\\d+)]]");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FakeLlmClientFactory fakeLlm;

    private StudyApiClient api;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM llm_settings");
        jdbcTemplate.update("DELETE FROM subjects");
        jdbcTemplate.update("DELETE FROM media_files");
        jdbcTemplate.update("DELETE FROM tags");
        fakeLlm.reset();
        api = new StudyApiClient(mvc, objectMapper);
    }

    @Test
    void markdownImportProducesSanitisedCandidatesForReview() throws Exception {
        long subjectId = api.createSubject("Java");
        fakeLlm.answerWith(request -> """
                {"items":[{"topicPath":"Java > Collections","type":"QUESTION","question":"What is a HashMap?",
                  "answerHtml":"<p>A <b>hash</b> table</p><script>alert(1)</script>","notesHtml":"",
                  "difficulty":"easy","tags":["Java"," Maps ","java"],"imageRefs":[],"confidence":0.85}]}""");

        JsonNode job = upload(subjectId, "notes.md", "# Java\n## Collections\nWhat is a HashMap?\n\nA hash table.\n"
                .getBytes(StandardCharsets.UTF_8));
        assertEquals("MD", job.get("fileType").asText());
        assertEquals("ANTHROPIC", job.get("llmProvider").asText());
        assertEquals("claude-opus-5-5", job.get("llmModel").asText());

        JsonNode finished = awaitImport(job.get("id").asLong());
        assertEquals("READY_FOR_REVIEW", finished.get("status").asText());
        assertEquals(1, finished.get("totalChunks").asInt());
        assertEquals(1, finished.get("processedChunks").asInt());
        assertEquals(100, finished.get("percentComplete").asInt());
        assertTrue(finished.get("inputTokens").asLong() > 0);
        assertEquals(1, finished.get("candidates").get("open").asInt());

        JsonNode candidate = candidates(job.get("id").asLong()).get(0);
        assertEquals("PENDING", candidate.get("status").asText());
        assertEquals("What is a HashMap?", candidate.get("title").asText());
        assertEquals("<p>A <b>hash</b> table</p>", candidate.get("answerHtml").asText());
        assertFalse(candidate.has("notesHtml"));
        assertEquals("EASY", candidate.get("difficulty").asText());
        assertEquals("[\"java\",\"maps\"]", candidate.get("tags").toString());
        assertEquals(0.85, candidate.get("confidence").asDouble(), 0.0001);
        assertEquals("Java > Collections", candidate.get("suggestedTopicPath").asText());

        LlmExtractionRequest request = fakeLlm.getRequests().getFirst();
        assertTrue(request.systemPrompt().contains("never write an answer"));
        assertTrue(request.userPrompt().contains("Format: Markdown"));
        assertTrue(request.userPrompt().contains("<document>\n# Java"));
        assertTrue(request.outputSchema().containsKey("properties"));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_items", Integer.class));
    }

    @Test
    void invalidJsonIsRetriedOnceWithACorrection() throws Exception {
        long subjectId = api.createSubject("Retry");
        AtomicInteger calls = new AtomicInteger();
        fakeLlm.answerWith(request -> calls.incrementAndGet() == 1
                ? "Sure! Here are the items: {"
                : "```json\n{\"items\":[{\"question\":\"Q?\",\"answerHtml\":\"A\"}]}\n```");

        JsonNode finished = awaitImport(upload(subjectId, "a.txt", "Q?\nA".getBytes(StandardCharsets.UTF_8))
                .get("id").asLong());

        assertEquals("READY_FOR_REVIEW", finished.get("status").asText());
        assertEquals(0, finished.get("failedChunks").asInt());
        List<LlmExtractionRequest> requests = fakeLlm.getRequests();
        assertEquals(2, requests.size());
        assertTrue(requests.get(1).isRetry());
        assertEquals("Sure! Here are the items: {", requests.get(1).previousAnswer());
        assertTrue(requests.get(1).correction().contains("not a valid JSON object"));
        JsonNode candidate = candidates(finished.get("id").asLong()).get(0);
        assertEquals("<p>A</p>", candidate.get("answerHtml").asText());
        assertEquals("QUESTION", candidate.get("itemType").asText());
    }

    @Test
    void chunkStillInvalidAfterRetryIsSkippedAndCounted() throws Exception {
        long subjectId = api.createSubject("Chunks");
        String markdown = "# Alpha\n" + "alpha text ".repeat(60) + "\n\n# Beta\n" + "beta text ".repeat(60) + "\n";
        fakeLlm.answerWith(request -> request.userPrompt().contains("# Alpha")
                ? "not json"
                : "{\"items\":[{\"topicPath\":\"Beta\",\"type\":\"NOTE\",\"question\":\"Beta\",\"answerHtml\":\"<p>b</p>\"}]}");

        JsonNode finished = awaitImport(upload(subjectId, "two.md", markdown.getBytes(StandardCharsets.UTF_8))
                .get("id").asLong());

        assertEquals("READY_FOR_REVIEW", finished.get("status").asText());
        assertEquals(2, finished.get("totalChunks").asInt());
        assertEquals(1, finished.get("failedChunks").asInt());
        assertEquals(3, fakeLlm.getRequests().size());
        assertEquals(1, candidates(finished.get("id").asLong()).size());
    }

    @Test
    void providerFailureFailsTheJobWithItsMessage() throws Exception {
        long subjectId = api.createSubject("Failing");
        fakeLlm.answerWith(request -> {
            throw new LlmException(LlmException.Reason.CONFIGURATION, "Anthropic rejected the API key (HTTP 401)");
        });

        JsonNode finished = awaitImport(upload(subjectId, "a.txt", "text".getBytes(StandardCharsets.UTF_8))
                .get("id").asLong());

        assertEquals("FAILED", finished.get("status").asText());
        assertEquals("Anthropic rejected the API key (HTTP 401)", finished.get("errorMessage").asText());
    }

    @Test
    void docxImagesBecomeMediaAndApprovedItemsUseThem() throws Exception {
        long subjectId = api.createSubject("Word");
        byte[] docx = TestDocuments.docxWithImage("Diagrams", "What does the flow look like?",
                TestDocuments.pngBytes(2, 2));
        fakeLlm.answerWith(request -> {
            Matcher placeholder = PLACEHOLDER.matcher(request.userPrompt());
            String image = placeholder.find() ? placeholder.group() : "";
            return """
                    {"items":[{"topicPath":"Diagrams > Flows","type":"DIAGRAM","question":"What does the flow look like?",
                      "answerHtml":"<p>%s</p>","notesHtml":"","difficulty":"MEDIUM","tags":[],"imageRefs":[],
                      "confidence":0.9}]}""".formatted(image);
        });

        JsonNode job = upload(subjectId, "flows.docx", docx);
        assertEquals("DOCX", job.get("fileType").asText());
        long jobId = awaitImport(job.get("id").asLong()).get("id").asLong();

        String prompt = fakeLlm.getRequests().getFirst().userPrompt();
        assertTrue(prompt.contains("Format: HTML"), prompt);
        assertTrue(prompt.contains("<h1>Diagrams</h1>"), prompt);
        assertTrue(prompt.contains("<strong>What does the flow look like?</strong>"), prompt);
        Matcher placeholder = PLACEHOLDER.matcher(prompt);
        assertTrue(placeholder.find(), prompt);
        long imageId = Long.parseLong(placeholder.group(1));

        JsonNode candidate = candidates(jobId).get(0);
        assertEquals("[" + imageId + "]", candidate.get("imageIds").toString());
        assertTrue(candidate.get("answerHtml").asText().contains("src=\"/api/v1/media/" + imageId + "\""));

        JsonNode approved = postJson("/api/v1/imports/" + jobId + "/approve", "{\"all\":true}", status().isOk());
        assertEquals(1, approved.get("approvedCount").asInt());
        assertEquals(2, approved.get("createdTopicCount").asInt());
        assertEquals("COMPLETED", approved.get("job").get("status").asText());
        long itemId = approved.get("createdItemIds").get(0).asLong();

        JsonNode item = readJson(get("/api/v1/items/" + itemId));
        assertEquals("IMPORT", item.get("source").asText());
        assertEquals("DIAGRAM", item.get("itemType").asText());
        assertEquals(itemId, jdbcTemplate.queryForObject("SELECT item_id FROM media_files WHERE id = ?", Long.class, imageId));
        assertEquals(jobId, jdbcTemplate.queryForObject("SELECT import_job_id FROM study_items WHERE id = ?", Long.class, itemId));
        JsonNode tree = readJson(get("/api/v1/subjects/" + subjectId + "/topics"));
        assertEquals("Diagrams", tree.get(0).get("name").asText());
        assertEquals("Flows", tree.get(0).get("children").get(0).get("name").asText());
        assertEquals(1, tree.get(0).get("children").get(0).get("itemCount").asInt());
    }

    @Test
    void reviewFlagsDuplicatesAndApprovesEditsAndRejects() throws Exception {
        long subjectId = api.createSubject("Review");
        long topicId = api.createTopic(subjectId, null, "JVM");
        long existingItemId = api.createItem(topicId, "What is the JVM", "EASY", "[]");
        fakeLlm.answerWith(request -> """
                {"items":[
                  {"topicPath":"JVM","type":"QUESTION","question":"What is the JVM?","answerHtml":"<p>A VM</p>"},
                  {"topicPath":"JIT","type":"QUESTION","question":"What is JIT?","answerHtml":"<p>Compiler</p>"},
                  {"topicPath":"JIT","type":"QUESTION","question":"what is  JIT?","answerHtml":"<p>Again</p>"}]}""");
        long jobId = awaitImport(upload(subjectId, "jvm.txt", "notes".getBytes(StandardCharsets.UTF_8))
                .get("id").asLong()).get("id").asLong();

        JsonNode candidates = candidates(jobId);
        assertEquals(existingItemId, candidates.get(0).get("duplicateItemId").asLong());
        assertFalse(candidates.get(1).has("duplicateOfCandidateId"));
        assertEquals(candidates.get(1).get("id").asLong(), candidates.get(2).get("duplicateOfCandidateId").asLong());

        long editedId = candidates.get(1).get("id").asLong();
        JsonNode edited = putJson("/api/v1/imports/" + jobId + "/candidates/" + editedId, """
                {"itemType":"NOTE","title":"Just-in-time compiler","questionHtml":"<p>What is JIT?</p>",
                 "answerHtml":"<p>Compiles <img src=\\"https://evil.example/x.png\\">hot code</p>",
                 "difficulty":"HARD","tags":["JIT"],"targetTopicId":%d}""".formatted(topicId), status().isOk());
        assertEquals("EDITED", edited.get("status").asText());
        assertEquals("<p>Compiles hot code</p>", edited.get("answerHtml").asText());

        JsonNode approved = postJson("/api/v1/imports/" + jobId + "/approve",
                "{\"candidateIds\":[" + editedId + "]}", status().isOk());
        assertEquals(1, approved.get("approvedCount").asInt());
        assertEquals(0, approved.get("createdTopicCount").asInt());
        assertEquals("READY_FOR_REVIEW", approved.get("job").get("status").asText());
        JsonNode item = readJson(get("/api/v1/items/" + approved.get("createdItemIds").get(0).asLong()));
        assertEquals(topicId, item.get("topicId").asLong());
        assertEquals("Just-in-time compiler", item.get("title").asText());
        assertEquals("[\"jit\"]", item.get("tags").toString());

        putJson("/api/v1/imports/" + jobId + "/candidates/" + editedId, """
                {"itemType":"NOTE","title":"Again"}""", status().isConflict());

        JsonNode rejected = postJson("/api/v1/imports/" + jobId + "/reject", "{\"all\":true}", status().isOk());
        assertEquals(2, rejected.get("rejectedCount").asInt());
        assertEquals("COMPLETED", rejected.get("job").get("status").asText());
        assertEquals(1, rejected.get("job").get("candidates").get("approved").asInt());
        assertEquals(2, rejected.get("job").get("candidates").get("rejected").asInt());

        JsonNode again = postJson("/api/v1/imports/" + jobId + "/approve", "{\"all\":true}", status().isOk());
        assertEquals(0, again.get("approvedCount").asInt());
        postJson("/api/v1/imports/" + jobId + "/approve", "{\"candidateIds\":[]}", status().isBadRequest());
        postJson("/api/v1/imports/" + jobId + "/approve", "{\"candidateIds\":[999999]}", status().isNotFound());
        assertEquals(1, readJson(get("/api/v1/imports")).size());
    }

    @Test
    void candidatesWithoutTopicPathGoToATopicNamedAfterTheFile() throws Exception {
        long subjectId = api.createSubject("Fallback");
        fakeLlm.answerWith(request -> "{\"items\":[{\"question\":\"Loose question?\",\"answerHtml\":\"\"}]}");
        long jobId = awaitImport(upload(subjectId, "loose-notes.txt", "Loose question?".getBytes(StandardCharsets.UTF_8))
                .get("id").asLong()).get("id").asLong();

        JsonNode approved = postJson("/api/v1/imports/" + jobId + "/approve", "{\"all\":true}", status().isOk());

        assertEquals(1, approved.get("createdTopicCount").asInt());
        JsonNode tree = readJson(get("/api/v1/subjects/" + subjectId + "/topics"));
        assertEquals("loose-notes", tree.get(0).get("name").asText());
    }

    @Test
    void pdfPagesAreSentAsText() throws Exception {
        long subjectId = api.createSubject("Pdf");
        byte[] pdf = TestDocuments.pdfWithPages(List.of("First page question", "Second page answer"));

        JsonNode finished = awaitImport(upload(subjectId, "scan.pdf", pdf).get("id").asLong());

        assertEquals("READY_FOR_REVIEW", finished.get("status").asText());
        String prompt = fakeLlm.getRequests().getFirst().userPrompt();
        assertTrue(prompt.contains("Format: plain text"), prompt);
        assertTrue(prompt.contains("[Page 1]\nFirst page question"), prompt);
        assertTrue(prompt.contains("[Page 2]\nSecond page answer"), prompt);
    }

    @Test
    void rejectsUnsupportedFilesAndUnavailableLlm() throws Exception {
        long subjectId = api.createSubject("Rejects");
        uploadExpecting(subjectId, "tool.exe", new byte[]{1, 2, 3}, status().isUnsupportedMediaType());
        uploadExpecting(subjectId, "fake.pdf", "plain text".getBytes(StandardCharsets.UTF_8),
                status().isUnsupportedMediaType());
        uploadExpecting(subjectId, "empty.txt", new byte[0], status().isBadRequest());
        uploadExpecting(subjectId, "big.txt", "x".repeat(1_048_577).getBytes(StandardCharsets.UTF_8),
                status().isPayloadTooLarge());
        uploadExpecting(999_999L, "a.txt", "a".getBytes(StandardCharsets.UTF_8), status().isNotFound());

        fakeLlm.failClientCreation(new LlmException(LlmException.Reason.CONFIGURATION,
                "ANTHROPIC_API_KEY is not set on the server"));
        uploadExpecting(subjectId, "a.txt", "a".getBytes(StandardCharsets.UTF_8), status().isConflict())
                .andExpect(jsonPath("$.message").value("ANTHROPIC_API_KEY is not set on the server"));
        fakeLlm.reset();

        putJson("/api/v1/settings/llm", "{\"provider\":\"OLLAMA\",\"model\":\"llama3.1\",\"enabled\":false}",
                status().isOk());
        uploadExpecting(subjectId, "a.txt", "a".getBytes(StandardCharsets.UTF_8), status().isConflict())
                .andExpect(jsonPath("$.message").value("Imports are switched off in Settings"));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM import_jobs", Integer.class));
        mvc.perform(get("/api/v1/imports/999999")).andExpect(status().isNotFound());
    }

    @Test
    void importUsesTheSavedProviderAndModel() throws Exception {
        long subjectId = api.createSubject("Ollama");
        putJson("/api/v1/settings/llm", "{\"provider\":\"OLLAMA\",\"model\":\"qwen2.5\",\"baseUrl\":\"http://gpu-box:11434/\"}",
                status().isOk());

        JsonNode finished = awaitImport(upload(subjectId, "a.txt", "a".getBytes(StandardCharsets.UTF_8))
                .get("id").asLong());

        assertEquals("OLLAMA", finished.get("llmProvider").asText());
        assertEquals("qwen2.5", finished.get("llmModel").asText());
        assertEquals("http://gpu-box:11434", fakeLlm.getCreatedFor().getLast().baseUrl());
    }

    private JsonNode upload(long subjectId, String fileName, byte[] content) throws Exception {
        String body = uploadExpecting(subjectId, fileName, content, status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private org.springframework.test.web.servlet.ResultActions uploadExpecting(long subjectId, String fileName,
                                                                              byte[] content, ResultMatcher expected)
            throws Exception {
        return mvc.perform(multipart("/api/v1/imports")
                        .file(new MockMultipartFile("file", fileName, "application/octet-stream", content))
                        .param("subjectId", String.valueOf(subjectId)))
                .andExpect(expected);
    }

    private JsonNode awaitImport(long jobId) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode job = readJson(get("/api/v1/imports/" + jobId));
            String status = job.get("status").asText();
            if (!List.of("UPLOADED", "PARSING", "EXTRACTING").contains(status)) {
                return job;
            }
            Thread.sleep(50);
        }
        return fail("Import " + jobId + " did not finish");
    }

    private JsonNode candidates(long jobId) throws Exception {
        return readJson(get("/api/v1/imports/" + jobId + "/candidates"));
    }

    private JsonNode readJson(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return objectMapper.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode postJson(String path, String json, ResultMatcher expected) throws Exception {
        String body = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(expected).andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : objectMapper.readTree(body);
    }

    private JsonNode putJson(String path, String json, ResultMatcher expected) throws Exception {
        String body = mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(expected).andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : objectMapper.readTree(body);
    }
}
