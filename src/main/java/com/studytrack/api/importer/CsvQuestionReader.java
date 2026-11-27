package com.studytrack.api.importer;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a CSV of questions: column 1 the question, column 2 the answer, optional column 3 the topic path
 * ("Java > Collections"). Follows RFC 4180: fields may be quoted, a quoted field may contain the delimiter,
 * line breaks and doubled quotes ({@code ""}). The delimiter is a comma, or a semicolon or tab when the first
 * line has none of the others (spreadsheets in some locales save that way). A UTF-8 byte order mark, blank
 * lines and a header row (first cells starting with "question" and "answer") are skipped, as are rows with
 * an empty question.
 */
@Component
public class CsvQuestionReader {
    static final int MAX_ROWS = 5000;
    private static final char QUOTE = '"';

    /** One usable row; {@code lineNumber} is the 1-based line where the row starts, for messages. */
    public record CsvQuestionRow(int lineNumber, String question, String answer, String topicPath) {
    }

    /** The rows that were read, and how many had no question and were skipped. */
    public record CsvQuestions(List<CsvQuestionRow> rows, int skippedRows) {
    }

    /** The file is not a readable CSV; the message is shown to the user. */
    public static class CsvFormatException extends RuntimeException {
        CsvFormatException(String message) {
            super(message);
        }
    }

    public CsvQuestions readQuestions(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        char delimiter = detectDelimiter(text);
        List<CsvRecord> records = splitRecords(text, delimiter);
        List<CsvQuestionRow> rows = new ArrayList<>();
        int skippedRows = 0;
        boolean firstRecord = true;
        for (CsvRecord csvRecord : records) {
            List<String> cells = csvRecord.cells();
            boolean isHeader = firstRecord && isHeaderRow(cells);
            firstRecord = false;
            if (isHeader || cells.stream().allMatch(String::isBlank)) {
                continue;
            }
            String question = cellAt(cells, 0);
            if (question == null) {
                skippedRows++;
                continue;
            }
            if (rows.size() == MAX_ROWS) {
                throw new CsvFormatException("The CSV has more than " + MAX_ROWS + " questions. Split it into smaller files.");
            }
            rows.add(new CsvQuestionRow(csvRecord.lineNumber(), question, cellAt(cells, 1), cellAt(cells, 2)));
        }
        return new CsvQuestions(List.copyOf(rows), skippedRows);
    }

    private record CsvRecord(int lineNumber, List<String> cells) {
    }

    /** Splits the text into records and cells, honouring quotes. */
    private static List<CsvRecord> splitRecords(String text, char delimiter) {
        List<CsvRecord> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        int line = 1;
        int recordStartLine = 1;
        int quoteStartLine = 1;
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (inQuotes) {
                if (current == QUOTE && index + 1 < text.length() && text.charAt(index + 1) == QUOTE) {
                    cell.append(QUOTE);
                    index++;
                } else if (current == QUOTE) {
                    inQuotes = false;
                } else {
                    if (current == '\n') {
                        line++;
                    }
                    cell.append(current);
                }
            } else if (current == QUOTE && cell.toString().isBlank()) {
                inQuotes = true;
                quoteStartLine = line;
                cell.setLength(0);
            } else if (current == delimiter) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else if (current == '\r' || current == '\n') {
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                cells.add(cell.toString());
                cell.setLength(0);
                records.add(new CsvRecord(recordStartLine, List.copyOf(cells)));
                cells.clear();
                line++;
                recordStartLine = line;
            } else {
                cell.append(current);
            }
            index++;
        }
        if (inQuotes) {
            throw new CsvFormatException("Line " + quoteStartLine + " opens a quote (\") that is never closed.");
        }
        if (!cells.isEmpty() || !cell.isEmpty()) {
            cells.add(cell.toString());
            records.add(new CsvRecord(recordStartLine, List.copyOf(cells)));
        }
        return records;
    }

    /** Comma, unless the first line has no comma but a semicolon or a tab. */
    private static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String firstLine = end < 0 ? text : text.substring(0, end);
        if (firstLine.indexOf(',') >= 0) {
            return ',';
        }
        if (firstLine.indexOf(';') >= 0) {
            return ';';
        }
        return firstLine.indexOf('\t') >= 0 ? '\t' : ',';
    }

    private static boolean isHeaderRow(List<String> cells) {
        if (cells.size() < 2) {
            return false;
        }
        return cells.get(0).strip().toLowerCase(Locale.ROOT).startsWith("question")
                && cells.get(1).strip().toLowerCase(Locale.ROOT).startsWith("answer");
    }

    /** The trimmed cell, or null when it is missing or blank. */
    private static String cellAt(List<String> cells, int position) {
        if (position >= cells.size()) {
            return null;
        }
        String value = cells.get(position).strip();
        return value.isEmpty() ? null : value;
    }
}
