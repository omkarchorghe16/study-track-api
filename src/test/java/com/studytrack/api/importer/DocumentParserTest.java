package com.studytrack.api.importer;

import com.studytrack.api.importer.DocumentParser.DocumentBlock;
import com.studytrack.api.importer.DocumentParser.ParsedDocument;
import com.studytrack.api.support.TestDocuments;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentParserTest {
    private final DocumentParser parser = new DocumentParser();

    @Test
    void markdownHeadingsStartSectionsButNotInsideCodeFences() {
        ParsedDocument document = DocumentParser.parseMarkdown("""
                # Java
                Intro line

                ## Streams
                ```java
                # not a heading

                stream.map(x -> x);
                ```
                #### Small heading
                """);

        List<DocumentBlock> blocks = document.blocks();
        assertEquals(1, blocks.get(0).headingLevel());
        assertEquals("Java", blocks.get(0).headingText());
        assertEquals("Intro line", blocks.get(1).content());
        assertEquals("Streams", blocks.get(2).headingText());
        assertTrue(blocks.get(3).content().contains("# not a heading\n\nstream.map"));
        assertEquals(0, blocks.get(4).headingLevel());
        assertEquals(5, blocks.size());
    }

    @Test
    void htmlImagesBecomePlaceholdersAndForeignImagesAreDropped() {
        List<DocumentBlock> blocks = DocumentParser.splitHtml(
                "<h2>Flow</h2><p>See <img src=\"import-image:42\"> and <img src=\"https://x/y.png\"></p><p> </p>");

        assertEquals(2, blocks.size());
        assertEquals("Flow", blocks.get(0).headingText());
        assertEquals("<p>See [[IMG:42]] and </p>", blocks.get(1).content());
    }

    @Test
    void docxImagesAreStoredThroughTheImageStore() throws Exception {
        byte[] docx = TestDocuments.docxWithImage("Title", "Body", TestDocuments.pngBytes(2, 2));

        ParsedDocument document = parser.parseDocument(ImportFileType.DOCX, docx, (bytes, alt) -> Optional.of(7L));

        assertEquals(List.of(7L), document.imageIds());
        assertEquals("<h1>Title</h1>", document.blocks().get(0).content());
        assertTrue(document.blocks().stream().anyMatch(block -> block.content().contains("[[IMG:7]]")));
    }

    @Test
    void unreadableFilesGiveAUserFacingError() {
        DocumentParser.DocumentParseException docx = assertThrows(DocumentParser.DocumentParseException.class,
                () -> parser.parseDocument(ImportFileType.DOCX, new byte[]{'P', 'K', 3, 4, 0}, (bytes, alt) -> Optional.empty()));
        DocumentParser.DocumentParseException pdf = assertThrows(DocumentParser.DocumentParseException.class,
                () -> parser.parseDocument(ImportFileType.PDF, "%PDF-broken".getBytes(), (bytes, alt) -> Optional.empty()));

        assertTrue(docx.getMessage().contains("Word document"));
        assertEquals("The PDF could not be read", pdf.getMessage());
    }

    @Test
    void detectsFileTypesFromNameAndContent() {
        assertEquals(Optional.of(ImportFileType.MD), ImportFileType.detectFileType("Notes.MARKDOWN", "# hi".getBytes()));
        assertEquals(Optional.empty(), ImportFileType.detectFileType("notes.txt", new byte[]{(byte) 0xC3, 0x28}));
        assertEquals(Optional.empty(), ImportFileType.detectFileType("notes.docx", "%PDF".getBytes()));
        assertEquals(Optional.of(ImportFileType.PDF), ImportFileType.detectFileType("a.pdf", "%PDF-1.7".getBytes()));
    }
}
