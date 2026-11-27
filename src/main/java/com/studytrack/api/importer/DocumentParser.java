package com.studytrack.api.importer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;
import org.zwobble.mammoth.DocumentConverter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an uploaded document into blocks: headings (levels 1-3, which start sections) and content.
 * DOCX goes through Mammoth to HTML; each embedded image is stored with {@link ImageStore} and replaced
 * by a {@code [[IMG:id]]} placeholder. PDF becomes one text block per page; Markdown and text are split
 * at blank lines (Markdown headings become heading blocks).
 */
@Component
public class DocumentParser {
    static final String IMAGE_SRC_PREFIX = "import-image:";
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final int MAX_SECTION_HEADING_LEVEL = 3;

    public enum DocumentFormat {
        HTML("HTML"),
        MARKDOWN("Markdown"),
        TEXT("plain text");

        private final String label;

        DocumentFormat(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Stores one embedded image for the importing user and returns its media id, or empty if it is not usable. */
    @FunctionalInterface
    public interface ImageStore {
        Optional<Long> storeImage(byte[] content, String altText);
    }

    /** {@code headingLevel} 1-3 marks a section heading (its text in {@code headingText}); 0 is content. */
    public record DocumentBlock(int headingLevel, String headingText, String content) {

        static DocumentBlock content(String content) {
            return new DocumentBlock(0, null, content);
        }

        boolean isHeading() {
            return headingLevel > 0;
        }
    }

    public record ParsedDocument(DocumentFormat format, List<DocumentBlock> blocks, List<Long> imageIds) {
    }

    public ParsedDocument parseDocument(ImportFileType fileType, byte[] content, ImageStore imageStore) {
        return switch (fileType) {
            case DOCX -> parseDocx(content, imageStore);
            case PDF -> parsePdf(content);
            case MD -> parseMarkdown(new String(content, StandardCharsets.UTF_8));
            case TXT -> parseText(new String(content, StandardCharsets.UTF_8));
            case CSV -> throw new IllegalArgumentException("CSV files are read by CsvQuestionReader, not parsed for the LLM");
        };
    }

    private ParsedDocument parseDocx(byte[] content, ImageStore imageStore) {
        List<Long> imageIds = new ArrayList<>();
        DocumentConverter converter = new DocumentConverter().imageConverter(image -> {
            byte[] imageBytes;
            try (InputStream input = image.getInputStream()) {
                imageBytes = input.readAllBytes();
            }
            Optional<Long> mediaId = imageStore.storeImage(imageBytes, image.getAltText().orElse(null));
            mediaId.ifPresent(imageIds::add);
            return mediaId.map(id -> Map.of("src", IMAGE_SRC_PREFIX + id)).orElse(Map.of());
        });
        String html;
        try {
            html = converter.convertToHtml(new ByteArrayInputStream(content)).getValue();
        } catch (IOException | RuntimeException exception) {
            throw new DocumentParseException("The Word document could not be read; save it again as .docx and retry",
                    exception);
        }
        return new ParsedDocument(DocumentFormat.HTML, splitHtml(html), imageIds);
    }

    /**
     * One block per top-level node; h1-h3 become heading blocks; images become placeholders. Loose
     * top-level text (Mammoth writes an image-only paragraph as a bare {@code <img>}) becomes a paragraph.
     */
    static List<DocumentBlock> splitHtml(String html) {
        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        for (Element image : document.select("img")) {
            String src = image.attr("src");
            if (src.startsWith(IMAGE_SRC_PREFIX)) {
                image.replaceWith(new TextNode("[[IMG:" + src.substring(IMAGE_SRC_PREFIX.length()) + "]]"));
            } else {
                image.remove();
            }
        }
        List<DocumentBlock> blocks = new ArrayList<>();
        for (Node node : document.body().childNodes()) {
            if (node instanceof TextNode text) {
                if (!text.isBlank()) {
                    blocks.add(DocumentBlock.content("<p>" + text.outerHtml().strip() + "</p>"));
                }
                continue;
            }
            if (!(node instanceof Element element)) {
                continue;
            }
            int level = headingLevel(element.tagName());
            if (level > 0 && level <= MAX_SECTION_HEADING_LEVEL && !element.text().isBlank()) {
                blocks.add(new DocumentBlock(level, element.text().strip(), element.outerHtml()));
            } else if (!element.text().isBlank()) {
                blocks.add(DocumentBlock.content(element.outerHtml()));
            }
        }
        return blocks;
    }

    private static int headingLevel(String tagName) {
        if (tagName.length() == 2 && tagName.charAt(0) == 'h' && Character.isDigit(tagName.charAt(1))) {
            return tagName.charAt(1) - '0';
        }
        return 0;
    }

    private ParsedDocument parsePdf(byte[] content) {
        List<DocumentBlock> blocks = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).strip();
                if (!text.isEmpty()) {
                    blocks.add(DocumentBlock.content("[Page " + page + "]\n" + text));
                }
            }
        } catch (InvalidPasswordException exception) {
            throw new DocumentParseException("The PDF is password-protected; remove the password and retry", exception);
        } catch (IOException | RuntimeException exception) {
            throw new DocumentParseException("The PDF could not be read", exception);
        }
        return new ParsedDocument(DocumentFormat.TEXT, blocks, List.of());
    }

    /** Headings start sections; fenced code blocks stay whole; other text splits at blank lines. */
    static ParsedDocument parseMarkdown(String markdown) {
        List<DocumentBlock> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        boolean inFence = false;
        for (String line : markdown.replace("\r\n", "\n").split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence;
                paragraph.append(line).append('\n');
                continue;
            }
            Matcher heading = MARKDOWN_HEADING.matcher(trimmed);
            if (!inFence && heading.matches()) {
                flushParagraph(paragraph, blocks);
                int level = heading.group(1).length();
                if (level <= MAX_SECTION_HEADING_LEVEL) {
                    blocks.add(new DocumentBlock(level, heading.group(2), line));
                } else {
                    blocks.add(DocumentBlock.content(line));
                }
            } else if (!inFence && trimmed.isEmpty()) {
                flushParagraph(paragraph, blocks);
            } else {
                paragraph.append(line).append('\n');
            }
        }
        flushParagraph(paragraph, blocks);
        return new ParsedDocument(DocumentFormat.MARKDOWN, blocks, List.of());
    }

    static ParsedDocument parseText(String text) {
        List<DocumentBlock> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
            if (line.isBlank()) {
                flushParagraph(paragraph, blocks);
            } else {
                paragraph.append(line).append('\n');
            }
        }
        flushParagraph(paragraph, blocks);
        return new ParsedDocument(DocumentFormat.TEXT, blocks, List.of());
    }

    private static void flushParagraph(StringBuilder paragraph, List<DocumentBlock> blocks) {
        String text = paragraph.toString().stripTrailing();
        if (!text.isBlank()) {
            blocks.add(DocumentBlock.content(text));
        }
        paragraph.setLength(0);
    }

    /** The document cannot be parsed; the message is shown to the user. */
    public static class DocumentParseException extends RuntimeException {
        public DocumentParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
