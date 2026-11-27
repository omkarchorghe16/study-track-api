package com.studytrack.api.importer;

import com.studytrack.api.content.CsvFormatException;
import com.studytrack.api.content.CsvRecordSplitter;
import com.studytrack.api.content.CsvRecordSplitter.CsvRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a CSV of questions: column 1 the question, column 2 the answer, optional column 3 the topic path
 * ("Java > Collections"). Quoting, delimiters and the byte order mark are handled by
 * {@link CsvRecordSplitter}. Blank lines and a header row (first cells starting with "question" and "answer")
 * are skipped, as are rows with an empty question.
 */
@Component
public class CsvQuestionReader {
    static final int MAX_ROWS = 5000;

    /** One usable row; {@code lineNumber} is the 1-based line where the row starts, for messages. */
    public record CsvQuestionRow(int lineNumber, String question, String answer, String topicPath) {
    }

    /** The rows that were read, and how many had no question and were skipped. */
    public record CsvQuestions(List<CsvQuestionRow> rows, int skippedRows) {
    }

    public CsvQuestions readQuestions(byte[] content) {
        List<CsvRecord> records = CsvRecordSplitter.splitRecords(content);
        List<CsvQuestionRow> rows = new ArrayList<>();
        int skippedRows = 0;
        boolean firstRecord = true;
        for (CsvRecord csvRecord : records) {
            boolean isHeader = firstRecord && isHeaderRow(csvRecord.cells());
            firstRecord = false;
            if (isHeader || csvRecord.isBlank()) {
                continue;
            }
            String question = csvRecord.cellAt(0);
            if (question == null) {
                skippedRows++;
                continue;
            }
            if (rows.size() == MAX_ROWS) {
                throw new CsvFormatException("The CSV has more than " + MAX_ROWS + " questions. Split it into smaller files.");
            }
            rows.add(new CsvQuestionRow(csvRecord.lineNumber(), question, csvRecord.cellAt(1), csvRecord.cellAt(2)));
        }
        return new CsvQuestions(List.copyOf(rows), skippedRows);
    }

    private static boolean isHeaderRow(List<String> cells) {
        if (cells.size() < 2) {
            return false;
        }
        return cells.get(0).strip().toLowerCase(Locale.ROOT).startsWith("question")
                && cells.get(1).strip().toLowerCase(Locale.ROOT).startsWith("answer");
    }
}
