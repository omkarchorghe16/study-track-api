package com.studytrack.api.content;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits CSV bytes into records and cells. Follows RFC 4180: fields may be quoted, a quoted field may contain
 * the delimiter, line breaks and doubled quotes ({@code ""}). The delimiter is a comma, or a semicolon or tab
 * when the first line has none of the others (spreadsheets in some locales save that way). A UTF-8 byte order
 * mark is dropped. Meaning of the columns is left to the caller.
 */
public final class CsvRecordSplitter {
    private static final char QUOTE = '"';
    private static final String BYTE_ORDER_MARK = "﻿";

    /** One record; {@code lineNumber} is the 1-based line where it starts, for messages. */
    public record CsvRecord(int lineNumber, List<String> cells) {
        public boolean isBlank() {
            return cells.stream().allMatch(String::isBlank);
        }

        /** The trimmed cell, or null when it is missing or blank. */
        public String cellAt(int position) {
            if (position >= cells.size()) {
                return null;
            }
            String value = cells.get(position).strip();
            return value.isEmpty() ? null : value;
        }
    }

    private CsvRecordSplitter() {
    }

    public static List<CsvRecord> splitRecords(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith(BYTE_ORDER_MARK)) {
            text = text.substring(1);
        }
        return splitRecords(text, detectDelimiter(text));
    }

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
}
