package com.studytrack.api.importer;

import com.studytrack.api.content.CsvFormatException;
import com.studytrack.api.importer.CsvQuestionReader.CsvQuestionRow;
import com.studytrack.api.importer.CsvQuestionReader.CsvQuestions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvQuestionReaderTest {
    private final CsvQuestionReader reader = new CsvQuestionReader();

    @Test
    void readsQuestionAnswerAndOptionalTopicSkippingHeaderAndBlankLines() {
        CsvQuestions questions = read("""
                ﻿Question,Answer,Topic
                What is a HashMap?,A hash table,Java > Collections

                What is the JVM?,The Java virtual machine
                """);

        assertEquals(2, questions.rows().size());
        assertEquals(new CsvQuestionRow(2, "What is a HashMap?", "A hash table", "Java > Collections"),
                questions.rows().get(0));
        CsvQuestionRow second = questions.rows().get(1);
        assertEquals("What is the JVM?", second.question());
        assertEquals("The Java virtual machine", second.answer());
        assertNull(second.topicPath());
        assertEquals(4, second.lineNumber());
    }

    @Test
    void quotedFieldsKeepCommasLineBreaksAndQuotes() {
        CsvQuestions questions = read("""
                "What does ""final"" mean, on a variable?","It can be assigned once.
                Only once."
                Next?,Yes\r
                """);

        assertEquals(2, questions.rows().size());
        assertEquals("What does \"final\" mean, on a variable?", questions.rows().get(0).question());
        assertEquals("It can be assigned once.\nOnly once.", questions.rows().get(0).answer());
        assertEquals("Yes", questions.rows().get(1).answer());
        assertEquals(3, questions.rows().get(1).lineNumber());
    }

    @Test
    void detectsSemicolonDelimiterAndSkipsRowsWithoutQuestion() {
        CsvQuestions questions = read("Frage eins?;Antwort eins\n;Antwort ohne Frage\nOnly a question\n");

        assertEquals(2, questions.rows().size());
        assertEquals("Antwort eins", questions.rows().get(0).answer());
        assertNull(questions.rows().get(1).answer());
        assertEquals(1, questions.skippedRows());
    }

    @Test
    void firstRowIsAQuestionWhenItIsNotAHeader() {
        assertEquals(1, read("Is this a question?,Yes").rows().size());
    }

    @Test
    void rejectsUnclosedQuotesAndTooManyRows() {
        CsvFormatException unclosed = assertThrows(CsvFormatException.class, () -> read("a,b\n\"open,never closed\n"));
        assertTrue(unclosed.getMessage().startsWith("Line 2 opens a quote"), unclosed.getMessage());

        String tooMany = "q,a\n".repeat(CsvQuestionReader.MAX_ROWS + 1);
        assertThrows(CsvFormatException.class, () -> read(tooMany));
    }

    private CsvQuestions read(String text) {
        return reader.readQuestions(text.getBytes(StandardCharsets.UTF_8));
    }
}
