package com.studytrack.api.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CatalogAnswerFormatterTest {

    @Test
    void blankTextGivesNull() {
        assertNull(CatalogAnswerFormatter.formatToHtml(null));
        assertNull(CatalogAnswerFormatter.formatToHtml("  \n "));
    }

    @Test
    void blankLinesSeparateParagraphsAndSingleBreaksBecomeLineBreaks() {
        assertEquals("<p>One<br>two</p><p>Three</p>", CatalogAnswerFormatter.formatToHtml("One\ntwo\n\nThree"));
    }

    @Test
    void dashLinesBecomeAListAndInlineCodeIsMarked() {
        assertEquals("<ul><li>Use <code>final</code></li><li>Second</li></ul>",
                CatalogAnswerFormatter.formatToHtml("- Use `final`\n- Second"));
    }

    @Test
    void fencedLinesBecomeAnEscapedCodeBlock() {
        assertEquals("<p>Example:</p><pre><code>List&lt;String&gt; names;\nint x = 1;</code></pre>",
                CatalogAnswerFormatter.formatToHtml("Example:\n```java\nList<String> names;\nint x = 1;\n```"));
    }

    @Test
    void htmlInTheTextIsEscaped() {
        assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>",
                CatalogAnswerFormatter.formatToHtml("<script>alert(1)</script>"));
    }
}
