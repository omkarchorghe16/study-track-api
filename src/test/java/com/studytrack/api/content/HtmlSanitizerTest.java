package com.studytrack.api.content;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlSanitizerTest {
    private final HtmlSanitizer htmlSanitizer = new HtmlSanitizer();

    @Test
    void keepsFormattingListsTablesAndCode() {
        String html = "<h2>Title</h2><p><strong>bold</strong> <em>it</em> <s>old</s> <u>u</u></p>"
                + "<ol><li data-list=\"bullet\">one</li></ol><pre data-language=\"java\">int x = 1;</pre>"
                + "<p><code>inline</code></p><table><tbody><tr><td>cell</td></tr></tbody></table><blockquote>q</blockquote>";
        assertEquals(html, htmlSanitizer.sanitizeHtml(html));
    }

    @Test
    void removesScriptsHandlersStylesAndJavascriptLinks() {
        String cleaned = htmlSanitizer.sanitizeHtml(
                "<p onclick=\"alert(1)\" style=\"color:red\">Hi<script>alert(1)</script></p>"
                        + "<a href=\"javascript:alert(1)\">bad</a><iframe src=\"https://evil.test\"></iframe>");
        assertEquals("<p>Hi</p><a rel=\"noopener noreferrer nofollow\">bad</a>", cleaned);
    }

    @Test
    void keepsOnlyStudyTrackMediaImages() {
        String cleaned = htmlSanitizer.sanitizeHtml("<p>x</p><img src=\"/api/v1/media/12\" alt=\"diagram\">"
                + "<img src=\"https://tracker.test/pixel.png\"><img src=\"data:image/png;base64,AAAA\">"
                + "<img src=\"/api/v1/media/12/../../evil\"><img src=\"/api/v1/media/abc\">");
        assertEquals("<p>x</p><img src=\"/api/v1/media/12\" alt=\"diagram\">", cleaned);
        assertEquals(List.of(12L), List.copyOf(htmlSanitizer.findMediaIds(cleaned)));
    }

    @Test
    void opensSafeLinksInNewTab() {
        String cleaned = htmlSanitizer.sanitizeHtml("<p><a href=\"https://docs.spring.io\">docs</a></p>");
        assertTrue(cleaned.contains("href=\"https://docs.spring.io\""));
        assertTrue(cleaned.contains("target=\"_blank\""));
        assertTrue(cleaned.contains("rel=\"noopener noreferrer nofollow\""));
    }

    @Test
    void returnsNullWhenNothingVisibleRemains() {
        assertNull(htmlSanitizer.sanitizeHtml("<p><br></p>"));
        assertNull(htmlSanitizer.sanitizeHtml("<script>alert(1)</script>"));
        assertNull(htmlSanitizer.sanitizeHtml("   "));
        assertFalse(htmlSanitizer.findMediaIds(null).iterator().hasNext());
        assertEquals("<img src=\"/api/v1/media/3\">", htmlSanitizer.sanitizeHtml("<img src=\"/api/v1/media/3\">"));
    }
}
