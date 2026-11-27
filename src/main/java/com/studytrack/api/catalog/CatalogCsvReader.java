package com.studytrack.api.catalog;

import com.studytrack.api.content.CsvFormatException;
import com.studytrack.api.content.CsvRecordSplitter;
import com.studytrack.api.content.CsvRecordSplitter.CsvRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a catalog CSV: column 1 the track, column 2 the subject, optional column 3 the topic path
 * ("Collections > HashMap"), and optional columns 4 to 6 for a starter question: the question, its answer
 * and its difficulty (EASY, MEDIUM or HARD). Blank lines and a header row (first cell starting with "track") are skipped.
 * Quoting and delimiters are handled by {@link CsvRecordSplitter}.
 */
@Component
public class CatalogCsvReader {
    static final int MAX_ROWS = 20000;

    /** One row; any column may be null when empty. {@code lineNumber} is the 1-based line, for messages. */
    public record CatalogCsvRow(int lineNumber, String track, String subject, String topicPath, String question,
                                String answer, String difficulty) {
    }

    public List<CatalogCsvRow> readRows(byte[] content) {
        List<CsvRecord> records = CsvRecordSplitter.splitRecords(content);
        List<CatalogCsvRow> rows = new ArrayList<>();
        boolean firstRecord = true;
        for (CsvRecord csvRecord : records) {
            boolean isHeader = firstRecord && isHeaderRow(csvRecord);
            firstRecord = false;
            if (isHeader || csvRecord.isBlank()) {
                continue;
            }
            if (rows.size() == MAX_ROWS) {
                throw new CsvFormatException("The CSV has more than " + MAX_ROWS + " rows. Split it into smaller files.");
            }
            rows.add(new CatalogCsvRow(csvRecord.lineNumber(), csvRecord.cellAt(0), csvRecord.cellAt(1),
                    csvRecord.cellAt(2), csvRecord.cellAt(3), csvRecord.cellAt(4), csvRecord.cellAt(5)));
        }
        return List.copyOf(rows);
    }

    private static boolean isHeaderRow(CsvRecord csvRecord) {
        String firstCell = csvRecord.cellAt(0);
        return firstCell != null && firstCell.toLowerCase(Locale.ROOT).startsWith("track");
    }
}
