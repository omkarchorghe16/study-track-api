package com.studytrack.api.content;

/** The file is not a readable CSV; the message is shown to the user. */
public class CsvFormatException extends IllegalArgumentException {
    public CsvFormatException(String message) {
        super(message);
    }
}
