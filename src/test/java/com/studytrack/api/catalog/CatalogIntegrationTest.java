package com.studytrack.api.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studytrack.api.catalog.CatalogDtos.CatalogImportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogIntegrationTest {
    private static final String CATALOG_CSV = """
            track,subject,topic_path
            Full-Stack Java Developer,Core Java,Collections > HashMap
            Full-Stack Java Developer,Core Java,Collections > TreeMap
            Full-Stack Java Developer,Core Java,Multithreading
            Full-Stack Java Developer,Spring Boot,
            Python Developer,Python Core,Generators
            ,Orphan subject,Topic
            """;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CatalogImportService catalogImportService;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM study_track.subjects");
        jdbcTemplate.update("DELETE FROM study_track.catalog_tracks");
        jdbcTemplate.update("UPDATE study_track.users SET is_admin = TRUE WHERE id = 1");
        jdbcTemplate.update("DELETE FROM study_track.users WHERE id <> 1");
    }

    @Test
    void adminImportAddsTracksSubjectsAndTopicsAndReportsBadRows() throws Exception {
        mvc.perform(importRequest(CATALOG_CSV))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsRead").value(6))
                .andExpect(jsonPath("$.tracksAdded").value(2))
                .andExpect(jsonPath("$.subjectsAdded").value(3))
                .andExpect(jsonPath("$.topicsAdded").value(5))
                .andExpect(jsonPath("$.rejectedRowCount").value(1))
                .andExpect(jsonPath("$.rejectedRows[0]").value("Line 7: needs a track and a subject"));

        mvc.perform(get("/api/v1/catalog/tracks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Full-Stack Java Developer"))
                .andExpect(jsonPath("$[0].subjectCount").value(2))
                .andExpect(jsonPath("$[1].name").value("Python Developer"));
    }

    @Test
    void importingTheSameFileAgainAddsNothingAndMatchesNamesIgnoringCase() throws Exception {
        mvc.perform(importRequest(CATALOG_CSV)).andExpect(status().isOk());

        mvc.perform(importRequest(CATALOG_CSV.replace("Core Java", "core java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracksAdded").value(0))
                .andExpect(jsonPath("$.subjectsAdded").value(0))
                .andExpect(jsonPath("$.topicsAdded").value(0));
    }

    @Test
    void nonAdminCannotImport() throws Exception {
        jdbcTemplate.update("UPDATE study_track.users SET is_admin = FALSE WHERE id = 1");

        mvc.perform(importRequest(CATALOG_CSV)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(false));
    }

    @Test
    void unreadableCsvIsABadRequest() throws Exception {
        mvc.perform(importRequest("Java,\"Core Java,Collections\n")).andExpect(status().isBadRequest());
    }

    @Test
    void browsesSubjectsAndTopicTreeInCsvOrder() throws Exception {
        mvc.perform(importRequest(CATALOG_CSV)).andExpect(status().isOk());
        long trackId = readTrackId("Full-Stack Java Developer");

        String subjectsBody = mvc.perform(get("/api/v1/catalog/tracks/" + trackId + "/subjects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Core Java"))
                .andExpect(jsonPath("$[0].topicCount").value(4))
                .andExpect(jsonPath("$[1].name").value("Spring Boot"))
                .andExpect(jsonPath("$[1].topicCount").value(0))
                .andReturn().getResponse().getContentAsString();
        long coreJavaId = objectMapper.readTree(subjectsBody).get(0).get("id").asLong();

        mvc.perform(get("/api/v1/catalog/subjects/" + coreJavaId + "/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Collections"))
                .andExpect(jsonPath("$[0].children[0].name").value("HashMap"))
                .andExpect(jsonPath("$[0].children[1].name").value("TreeMap"))
                .andExpect(jsonPath("$[1].name").value("Multithreading"))
                .andExpect(jsonPath("$[1].children.length()").value(0));
        mvc.perform(get("/api/v1/catalog/tracks/999999/subjects")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/catalog/subjects/999999/topics")).andExpect(status().isNotFound());
    }

    @Test
    void enrollCopiesTheSubjectWithItsTopicTreeAndCannotBeRepeated() throws Exception {
        mvc.perform(importRequest(CATALOG_CSV)).andExpect(status().isOk());
        long coreJavaId = readCoreJavaId();

        String body = mvc.perform(post("/api/v1/catalog/subjects/" + coreJavaId + "/enroll"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/subjects/")))
                .andExpect(jsonPath("$.name").value("Core Java"))
                .andExpect(jsonPath("$.topicCount").value(4))
                .andReturn().getResponse().getContentAsString();
        long subjectId = objectMapper.readTree(body).get("id").asLong();

        mvc.perform(get("/api/v1/subjects/" + subjectId + "/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Collections"))
                .andExpect(jsonPath("$[0].children[1].name").value("TreeMap"))
                .andExpect(jsonPath("$[1].name").value("Multithreading"));
        mvc.perform(get("/api/v1/catalog/tracks/" + readTrackId("Full-Stack Java Developer") + "/subjects"))
                .andExpect(jsonPath("$[0].copiedSubjectId").value(subjectId))
                .andExpect(jsonPath("$[1].copiedSubjectId").doesNotExist());
        mvc.perform(post("/api/v1/catalog/subjects/" + coreJavaId + "/enroll")).andExpect(status().isConflict());
    }

    @Test
    void enrollIsRefusedWhenTheUserHasASubjectWithTheSameName() throws Exception {
        mvc.perform(importRequest(CATALOG_CSV)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/subjects").contentType("application/json").content("{\"name\":\"Core Java\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/catalog/subjects/" + readCoreJavaId() + "/enroll"))
                .andExpect(status().isConflict());
    }

    @Test
    void servesACsvTemplate() throws Exception {
        mvc.perform(get("/api/v1/catalog/template.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("catalog-template.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("track,subject,topic_path")));
    }

    @Test
    void shippedSeedFilesImportWithoutRejectedRows() throws IOException {
        Resource[] seedFiles = new PathMatchingResourcePatternResolver().getResources("classpath:catalog/*.csv");
        assertTrue(seedFiles.length >= 4);
        int tracksAdded = 0;
        for (Resource seedFile : seedFiles) {
            CatalogImportResponse result = catalogImportService.importCatalogCsv(seedFile.getContentAsByteArray());
            assertEquals(0, result.rejectedRowCount(), seedFile.getFilename() + ": " + result.rejectedRows());
            tracksAdded += result.tracksAdded();
        }
        assertEquals(seedFiles.length, tracksAdded);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder importRequest(String csv) {
        return multipart("/api/v1/admin/catalog/import")
                .file(new MockMultipartFile("file", "catalog.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
    }

    private long readTrackId(String name) throws Exception {
        String body = mvc.perform(get("/api/v1/catalog/tracks")).andReturn().getResponse().getContentAsString();
        for (JsonNode track : objectMapper.readTree(body)) {
            if (track.get("name").asText().equals(name)) {
                return track.get("id").asLong();
            }
        }
        throw new AssertionError("No track " + name);
    }

    private long readCoreJavaId() throws Exception {
        String body = mvc.perform(get("/api/v1/catalog/tracks/" + readTrackId("Full-Stack Java Developer") + "/subjects"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get(0).get("id").asLong();
    }
}
