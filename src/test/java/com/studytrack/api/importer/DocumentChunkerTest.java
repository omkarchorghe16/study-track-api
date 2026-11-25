package com.studytrack.api.importer;

import com.studytrack.api.importer.DocumentChunker.DocumentChunk;
import com.studytrack.api.importer.DocumentParser.DocumentBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentChunkerTest {
    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void packsSmallSectionsAndKeepsTheHeadingPathWhereEachChunkStarts() {
        List<DocumentBlock> blocks = List.of(
                new DocumentBlock(1, "Spring Boot", "<h1>Spring Boot</h1>"),
                DocumentBlock.content("<p>" + "a".repeat(100) + "</p>"),
                new DocumentBlock(2, "Security", "<h2>Security</h2>"),
                DocumentBlock.content("<p>" + "b".repeat(100) + "</p>"),
                new DocumentBlock(3, "JWT", "<h3>JWT</h3>"),
                DocumentBlock.content("<p>" + "c".repeat(150) + "</p>"),
                new DocumentBlock(2, "Data", "<h2>Data</h2>"),
                DocumentBlock.content("<p>" + "d".repeat(100) + "</p>"));

        List<DocumentChunk> chunks = chunker.splitIntoChunks(blocks, 70);

        assertEquals(List.of("Spring Boot", "Spring Boot > Security > JWT", "Spring Boot > Data"),
                chunks.stream().map(DocumentChunk::headingPath).toList());
        assertTrue(chunks.get(0).content().contains("<h2>Security</h2>"));
        assertEquals(List.of(0, 1, 2), chunks.stream().map(DocumentChunk::index).toList());
        chunks.forEach(chunk -> assertTrue(chunk.content().length() <= 280, chunk.content()));
    }

    @Test
    void splitsSectionsAndBlocksLargerThanTheLimit() {
        String longBlock = ("word ".repeat(30) + "\n").repeat(10);
        List<DocumentBlock> blocks = List.of(
                new DocumentBlock(1, "Big", "# Big"),
                DocumentBlock.content(longBlock),
                DocumentBlock.content("tail paragraph"));

        List<DocumentChunk> chunks = chunker.splitIntoChunks(blocks, 100);

        assertTrue(chunks.size() > 3);
        chunks.forEach(chunk -> {
            assertEquals("Big", chunk.headingPath());
            assertTrue(chunk.content().length() <= 400);
        });
        String joined = String.join(" ", chunks.stream().map(DocumentChunk::content).toList());
        assertEquals(300, joined.split("word", -1).length - 1);
        assertTrue(chunks.getLast().content().endsWith("tail paragraph"));
    }

    @Test
    void textWithoutHeadingsHasAnEmptyPath() {
        List<DocumentChunk> chunks = chunker.splitIntoChunks(DocumentParser.parseText("one\n\ntwo").blocks(), 100);

        assertEquals(1, chunks.size());
        assertEquals("", chunks.getFirst().headingPath());
        assertEquals("one\ntwo", chunks.getFirst().content());
    }

    @Test
    void estimatesFourCharactersPerToken() {
        assertEquals(3, DocumentChunker.estimateTokens("123456789"));
    }
}
