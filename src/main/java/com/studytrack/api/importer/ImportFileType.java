package com.studytrack.api.importer;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Optional;

/** Document formats an import accepts. The extension picks the type; the bytes must agree with it. */
public enum ImportFileType {
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("application/pdf"),
    MD("text/markdown"),
    TXT("text/plain"),
    /** Questions in rows: question, answer, optional topic path. Read directly, without an LLM. */
    CSV("text/csv");

    private final String contentType;

    ImportFileType(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }

    /** Whether the file is sent to an LLM; CSV rows are already questions and answers. */
    public boolean usesLlm() {
        return this != CSV;
    }

    /**
     * The type for a file name ending in .docx, .pdf, .md/.markdown, .txt or .csv whose content matches: a ZIP
     * container for DOCX, a {@code %PDF} header for PDF and valid UTF-8 text for MD, TXT and CSV.
     */
    public static Optional<ImportFileType> detectFileType(String fileName, byte[] content) {
        if (fileName == null) {
            return Optional.empty();
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        ImportFileType byName;
        if (lowerName.endsWith(".docx")) {
            byName = DOCX;
        } else if (lowerName.endsWith(".pdf")) {
            byName = PDF;
        } else if (lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) {
            byName = MD;
        } else if (lowerName.endsWith(".txt")) {
            byName = TXT;
        } else if (lowerName.endsWith(".csv")) {
            byName = CSV;
        } else {
            return Optional.empty();
        }
        return byName.matchesContent(content) ? Optional.of(byName) : Optional.empty();
    }

    private boolean matchesContent(byte[] content) {
        return switch (this) {
            case DOCX -> startsWith(content, new byte[]{'P', 'K', 3, 4});
            case PDF -> startsWith(content, new byte[]{'%', 'P', 'D', 'F'});
            case MD, TXT, CSV -> isUtf8Text(content);
        };
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUtf8Text(byte[] content) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
            return text.indexOf('\0') < 0;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }
}
