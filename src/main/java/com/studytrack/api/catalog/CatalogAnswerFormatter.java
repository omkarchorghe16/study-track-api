package com.studytrack.api.catalog;

import org.jsoup.nodes.Entities;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns the plain text of a catalog question or answer into HTML: blank lines separate paragraphs, a block of
 * lines that all start with "- " is a list, `code` is inline code, and lines between two ``` fences are a code
 * block. All text is escaped; the item service sanitises the result again before it is saved.
 */
final class CatalogAnswerFormatter {
    private static final String FENCE = "```";
    private static final String LIST_MARKER = "- ";
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`\\n]+)`");

    private CatalogAnswerFormatter() {
    }

    /** Null or blank text gives null. */
    static String formatToHtml(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        StringBuilder html = new StringBuilder();
        List<String> paragraph = new ArrayList<>();
        List<String> codeLines = new ArrayList<>();
        boolean inCodeBlock = false;
        for (String line : plainText.replace("\r\n", "\n").split("\n", -1)) {
            if (line.strip().startsWith(FENCE)) {
                if (inCodeBlock) {
                    html.append("<pre><code>").append(Entities.escape(String.join("\n", codeLines)))
                            .append("</code></pre>");
                    codeLines.clear();
                } else {
                    appendParagraph(html, paragraph);
                }
                inCodeBlock = !inCodeBlock;
            } else if (inCodeBlock) {
                codeLines.add(line);
            } else if (line.isBlank()) {
                appendParagraph(html, paragraph);
            } else {
                paragraph.add(line.strip());
            }
        }
        if (inCodeBlock) {
            html.append("<pre><code>").append(Entities.escape(String.join("\n", codeLines))).append("</code></pre>");
        }
        appendParagraph(html, paragraph);
        return html.toString();
    }

    private static void appendParagraph(StringBuilder html, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        if (lines.stream().allMatch(line -> line.startsWith(LIST_MARKER))) {
            html.append("<ul>");
            lines.forEach(line -> html.append("<li>").append(formatInline(line.substring(LIST_MARKER.length())))
                    .append("</li>"));
            html.append("</ul>");
        } else {
            html.append("<p>").append(String.join("<br>", lines.stream().map(CatalogAnswerFormatter::formatInline).toList()))
                    .append("</p>");
        }
        lines.clear();
    }

    private static String formatInline(String text) {
        return INLINE_CODE.matcher(Entities.escape(text)).replaceAll("<code>$1</code>");
    }
}
