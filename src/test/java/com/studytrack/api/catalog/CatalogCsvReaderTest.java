package com.studytrack.api.catalog;

import com.studytrack.api.catalog.CatalogCsvReader.CatalogCsvRow;
import com.studytrack.api.content.CsvFormatException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogCsvReaderTest {
    private final CatalogCsvReader reader = new CatalogCsvReader();

    private List<CatalogCsvRow> read(String text) {
        return reader.readRows(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void skipsHeaderAndBlankLinesAndReadsOptionalTopicPath() {
        List<CatalogCsvRow> rows = read("﻿Track,Subject,Topic_Path\n\nJava,Core Java,Collections > HashMap\nJava,Spring Boot\n");

        assertEquals(2, rows.size());
        assertEquals("Java", rows.get(0).track());
        assertEquals("Collections > HashMap", rows.get(0).topicPath());
        assertEquals(4, rows.get(0).lineNumber() + 1);
        assertEquals("Spring Boot", rows.get(1).subject());
        assertNull(rows.get(1).topicPath());
    }

    @Test
    void readsQuotedCellsWithCommas() {
        List<CatalogCsvRow> rows = read("Java,Core Java,\"String, StringBuilder and StringBuffer\"\n");

        assertEquals("String, StringBuilder and StringBuffer", rows.get(0).topicPath());
    }

    @Test
    void keepsRowsWithMissingTrackOrSubjectSoTheyCanBeReported() {
        List<CatalogCsvRow> rows = read("Java,,Collections\n,Core Java,Collections\n");

        assertEquals(2, rows.size());
        assertNull(rows.get(0).subject());
        assertNull(rows.get(1).track());
    }

    @Test
    void rejectsUnclosedQuote() {
        assertThrows(CsvFormatException.class, () -> read("Java,\"Core Java,Collections\n"));
    }

    @Test
    void rejectsMoreRowsThanTheLimit() {
        String tooMany = "Java,Core Java,Topic\n".repeat(CatalogCsvReader.MAX_ROWS + 1);

        assertThrows(CsvFormatException.class, () -> read(tooMany));
    }
}
