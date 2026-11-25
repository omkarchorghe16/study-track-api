package com.studytrack.api.item;

import org.jsoup.Jsoup;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Derived columns of a study item: plain-text answer and duplicate-detection hash. */
final class ItemContent {
    private ItemContent() {
    }

    /** Visible text of the HTML with tags removed and whitespace collapsed; null when there is none. */
    static String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String text = Jsoup.parse(html).text().strip();
        return text.isEmpty() ? null : text;
    }

    /**
     * SHA-256 (hex) of the question text, or the title when there is no question, lower-cased with
     * whitespace collapsed, so the same question typed with different formatting gets the same hash.
     */
    static String computeContentHash(String title, String questionHtml) {
        String questionText = toPlainText(questionHtml);
        String source = questionText != null ? questionText : title;
        String normalized = source.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
