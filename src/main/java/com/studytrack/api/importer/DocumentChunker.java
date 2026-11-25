package com.studytrack.api.importer;

import com.studytrack.api.importer.DocumentParser.DocumentBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Splits parsed blocks into LLM-sized chunks. Headings (levels 1-3) start sections, and the heading path
 * ("Spring Boot > Security") goes with each chunk so the LLM knows where the text sits. Small sections
 * are packed together up to the token limit; a section larger than the limit is split between blocks,
 * and a single block larger than the limit at line or word breaks.
 */
@Component
public class DocumentChunker {
    /** Rough size of one token for English text and HTML; good enough to stay under the limit. */
    static final int CHARACTERS_PER_TOKEN = 4;
    static final String PATH_SEPARATOR = " > ";
    private static final int MAX_LEVELS = 3;

    /** {@code headingPath} is the section path where the chunk starts, or empty at the top of the document. */
    public record DocumentChunk(int index, String headingPath, String content) {
    }

    private record Section(String headingPath, List<String> blocks) {
    }

    public List<DocumentChunk> splitIntoChunks(List<DocumentBlock> blocks, int maxTokens) {
        int maxCharacters = maxTokens * CHARACTERS_PER_TOKEN;
        List<DocumentChunk> chunks = new ArrayList<>();
        String chunkPath = null;
        StringBuilder chunk = new StringBuilder();
        for (Section section : buildSections(blocks)) {
            String sectionText = String.join("\n", section.blocks());
            if (sectionText.length() > maxCharacters) {
                addChunk(chunks, chunkPath, chunk);
                chunkPath = null;
                splitLargeSection(section, maxCharacters, chunks);
                continue;
            }
            int separatorLength = chunk.isEmpty() ? 0 : 1;
            if (chunk.length() + separatorLength + sectionText.length() > maxCharacters) {
                addChunk(chunks, chunkPath, chunk);
                chunkPath = null;
            }
            if (chunk.isEmpty()) {
                chunkPath = section.headingPath();
            } else {
                chunk.append('\n');
            }
            chunk.append(sectionText);
        }
        addChunk(chunks, chunkPath, chunk);
        return chunks;
    }

    static int estimateTokens(String text) {
        return (text.length() + CHARACTERS_PER_TOKEN - 1) / CHARACTERS_PER_TOKEN;
    }

    private static List<Section> buildSections(List<DocumentBlock> blocks) {
        List<Section> sections = new ArrayList<>();
        String[] path = new String[MAX_LEVELS];
        Section current = new Section("", new ArrayList<>());
        for (DocumentBlock block : blocks) {
            if (block.isHeading()) {
                if (!current.blocks().isEmpty()) {
                    sections.add(current);
                }
                int level = Math.min(block.headingLevel(), MAX_LEVELS);
                path[level - 1] = block.headingText();
                Arrays.fill(path, level, MAX_LEVELS, null);
                current = new Section(joinPath(path), new ArrayList<>());
            }
            current.blocks().add(block.content());
        }
        if (!current.blocks().isEmpty()) {
            sections.add(current);
        }
        return sections;
    }

    private static String joinPath(String[] path) {
        return Arrays.stream(path).filter(Objects::nonNull).collect(Collectors.joining(PATH_SEPARATOR));
    }

    private static void splitLargeSection(Section section, int maxCharacters, List<DocumentChunk> chunks) {
        StringBuilder piece = new StringBuilder();
        for (String block : section.blocks()) {
            if (block.length() > maxCharacters) {
                addChunk(chunks, section.headingPath(), piece);
                for (String part : splitText(block, maxCharacters)) {
                    chunks.add(new DocumentChunk(chunks.size(), section.headingPath(), part));
                }
                continue;
            }
            if (piece.length() + 1 + block.length() > maxCharacters) {
                addChunk(chunks, section.headingPath(), piece);
            }
            if (!piece.isEmpty()) {
                piece.append('\n');
            }
            piece.append(block);
        }
        addChunk(chunks, section.headingPath(), piece);
    }

    /** Cuts text into parts of at most {@code maxCharacters}, preferring a line break, then a space. */
    static List<String> splitText(String text, int maxCharacters) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxCharacters, text.length());
            if (end < text.length()) {
                int minimumEnd = start + maxCharacters / 2;
                int lineBreak = text.lastIndexOf('\n', end - 1);
                int space = text.lastIndexOf(' ', end - 1);
                if (lineBreak >= minimumEnd) {
                    end = lineBreak + 1;
                } else if (space >= minimumEnd) {
                    end = space + 1;
                }
            }
            String part = text.substring(start, end).strip();
            if (!part.isEmpty()) {
                parts.add(part);
            }
            start = end;
        }
        return parts;
    }

    private static void addChunk(List<DocumentChunk> chunks, String headingPath, StringBuilder content) {
        if (!content.isEmpty()) {
            chunks.add(new DocumentChunk(chunks.size(), headingPath == null ? "" : headingPath, content.toString()));
            content.setLength(0);
        }
    }
}
