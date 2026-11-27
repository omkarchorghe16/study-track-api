package com.studytrack.api.importer;

import com.studytrack.api.content.HtmlSanitizer;
import com.studytrack.api.importer.DocumentChunker.DocumentChunk;
import com.studytrack.api.importer.ExtractionResultParser.ExtractedItem;
import com.studytrack.api.item.Difficulty;
import com.studytrack.api.item.ItemContent;
import com.studytrack.api.item.ItemType;
import com.studytrack.api.media.MediaService;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns an item from the LLM into candidate fields: placeholders become {@code <img>} tags for the
 * job's own images only, plain text becomes paragraphs, HTML is sanitised, and title, tags, topic path
 * and duplicate hash are derived. Edited candidates go through the same cleaning.
 */
@Component
public class CandidateContentBuilder {
    static final int MAX_TITLE_LENGTH = 300;
    static final int MAX_TOPIC_PATH_LENGTH = 500;
    static final int MAX_TAGS = 10;
    static final int MAX_TAG_LENGTH = 50;
    private static final Pattern IMAGE_PLACEHOLDER = Pattern.compile("\\[\\[IMG:(\\d{1,18})]]");
    private static final Pattern HTML_TAG = Pattern.compile("<[a-zA-Z/][^>]*>");

    private final HtmlSanitizer htmlSanitizer;

    public CandidateContentBuilder(HtmlSanitizer htmlSanitizer) {
        this.htmlSanitizer = htmlSanitizer;
    }

    /** Cleaned fields of a candidate, from the LLM or from the user's edit. */
    public record CandidateContent(
            ItemType itemType,
            String title,
            String questionHtml,
            String answerHtml,
            String notesHtml,
            Difficulty difficulty,
            List<String> tags,
            String topicPath) {
    }

    /** The candidate for one extracted item, or empty when the item has no text at all. */
    public Optional<ImportCandidateEntity> buildCandidate(Long jobId, DocumentChunk chunk, ExtractedItem item,
                                                          Set<Long> jobImageIds, int sortOrder) {
        String questionHtml = toSafeHtml(item.question(), jobImageIds);
        String answerHtml = toSafeHtml(item.answerHtml(), jobImageIds);
        String notesHtml = toSafeHtml(item.notesHtml(), jobImageIds);
        answerHtml = appendUnplacedImages(answerHtml, item.imageRefs(), jobImageIds, questionHtml, notesHtml);
        if (questionHtml == null && answerHtml == null && notesHtml == null) {
            return Optional.empty();
        }
        String topicPath = item.topicPath() == null || item.topicPath().isBlank()
                ? chunk.headingPath()
                : item.topicPath();
        CandidateContent content = new CandidateContent(parseItemType(item.type()), null, questionHtml, answerHtml,
                notesHtml, parseDifficulty(item.difficulty()), item.tags(), topicPath);
        ImportCandidateEntity candidate = new ImportCandidateEntity(jobId, chunk.index());
        applyContent(candidate, content);
        candidate.setConfidenceScore(toConfidence(item.confidence()));
        candidate.setSortOrder(sortOrder);
        return Optional.of(candidate);
    }

    /**
     * The candidate for one CSV row. Cells are plain text: HTML-looking text is escaped and shown as typed,
     * blank lines become paragraphs. Difficulty starts at MEDIUM and there is no LLM confidence.
     */
    public ImportCandidateEntity buildCsvCandidate(Long jobId, CsvQuestionReader.CsvQuestionRow row, int sortOrder) {
        CandidateContent content = new CandidateContent(ItemType.QUESTION, null, plainTextToHtml(row.question()),
                row.answer() == null ? null : plainTextToHtml(row.answer()), null, Difficulty.MEDIUM, List.of(),
                row.topicPath());
        ImportCandidateEntity candidate = new ImportCandidateEntity(jobId, 0);
        applyContent(candidate, content);
        candidate.setSortOrder(sortOrder);
        return candidate;
    }

    /**
     * Writes cleaned content to the candidate. HTML must already be safe or come from the user (it is
     * sanitised again here); a null title is derived from the question, notes or answer.
     */
    public void applyContent(ImportCandidateEntity candidate, CandidateContent content) {
        String questionHtml = htmlSanitizer.sanitizeHtml(content.questionHtml());
        String answerHtml = htmlSanitizer.sanitizeHtml(content.answerHtml());
        String notesHtml = htmlSanitizer.sanitizeHtml(content.notesHtml());
        String title = content.title() == null || content.title().isBlank()
                ? deriveTitle(questionHtml, notesHtml, answerHtml)
                : truncate(content.title().strip(), MAX_TITLE_LENGTH);
        candidate.setItemType(content.itemType() == null ? ItemType.QUESTION : content.itemType());
        candidate.setTitle(title);
        candidate.setQuestionHtml(questionHtml);
        candidate.setAnswerHtml(answerHtml);
        candidate.setNotesHtml(notesHtml);
        candidate.setDifficulty(content.difficulty() == null ? Difficulty.MEDIUM : content.difficulty());
        candidate.setTagList(normalizeTags(content.tags()));
        candidate.setSuggestedTopicPath(normalizeTopicPath(content.topicPath()));
        Set<Long> imageIds = new LinkedHashSet<>();
        imageIds.addAll(htmlSanitizer.findMediaIds(questionHtml));
        imageIds.addAll(htmlSanitizer.findMediaIds(answerHtml));
        imageIds.addAll(htmlSanitizer.findMediaIds(notesHtml));
        candidate.setImageIdList(new ArrayList<>(imageIds));
        candidate.setContentHash(ItemContent.computeContentHash(title, questionHtml));
    }

    /** Splits "A > B > C" into trimmed names, dropping empty parts. */
    public static List<String> splitTopicPath(String topicPath) {
        if (topicPath == null || topicPath.isBlank()) {
            return List.of();
        }
        return Arrays.stream(topicPath.split(">")).map(String::strip).filter(part -> !part.isEmpty()).toList();
    }

    private String toSafeHtml(String value, Set<Long> jobImageIds) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String html = HTML_TAG.matcher(value).find() ? value : plainTextToHtml(value);
        Matcher placeholder = IMAGE_PLACEHOLDER.matcher(html);
        StringBuilder replaced = new StringBuilder();
        while (placeholder.find()) {
            long mediaId = Long.parseLong(placeholder.group(1));
            String image = jobImageIds.contains(mediaId) ? imageTag(mediaId) : "";
            placeholder.appendReplacement(replaced, Matcher.quoteReplacement(image));
        }
        placeholder.appendTail(replaced);
        return htmlSanitizer.sanitizeHtml(replaced.toString());
    }

    /** Images the LLM listed in imageRefs but did not place in any field are added at the end of the answer. */
    private String appendUnplacedImages(String answerHtml, List<Long> imageRefs, Set<Long> jobImageIds,
                                        String questionHtml, String notesHtml) {
        if (imageRefs == null || imageRefs.isEmpty()) {
            return answerHtml;
        }
        Set<Long> placed = new LinkedHashSet<>();
        placed.addAll(htmlSanitizer.findMediaIds(questionHtml));
        placed.addAll(htmlSanitizer.findMediaIds(answerHtml));
        placed.addAll(htmlSanitizer.findMediaIds(notesHtml));
        String missing = imageRefs.stream()
                .filter(id -> id != null && jobImageIds.contains(id) && !placed.contains(id))
                .distinct()
                .map(id -> "<p>" + imageTag(id) + "</p>")
                .collect(Collectors.joining());
        if (missing.isEmpty()) {
            return answerHtml;
        }
        return htmlSanitizer.sanitizeHtml((answerHtml == null ? "" : answerHtml) + missing);
    }

    private static String imageTag(long mediaId) {
        return "<img src=\"" + MediaService.MEDIA_URL_PREFIX + mediaId + "\" alt=\"\">";
    }

    /** Paragraphs at blank lines, line breaks kept; keeps [[IMG:n]] placeholders intact. */
    private static String plainTextToHtml(String text) {
        return Arrays.stream(text.strip().split("\\n\\s*\\n"))
                .map(paragraph -> "<p>" + Entities.escape(paragraph.strip()).replace("\n", "<br>") + "</p>")
                .collect(Collectors.joining());
    }

    private static String deriveTitle(String... htmlFields) {
        for (String html : htmlFields) {
            String text = ItemContent.toPlainText(html);
            if (text != null) {
                return truncate(text, MAX_TITLE_LENGTH);
            }
        }
        return "Untitled";
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s,]+", " ").strip())
                .filter(tag -> !tag.isEmpty() && tag.length() <= MAX_TAG_LENGTH)
                .distinct()
                .limit(MAX_TAGS)
                .toList();
    }

    private static String normalizeTopicPath(String topicPath) {
        List<String> parts = splitTopicPath(topicPath);
        if (parts.isEmpty()) {
            return null;
        }
        return truncate(String.join(DocumentChunker.PATH_SEPARATOR, parts), MAX_TOPIC_PATH_LENGTH);
    }

    private static ItemType parseItemType(String type) {
        try {
            return type == null ? ItemType.QUESTION : ItemType.valueOf(type.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ItemType.QUESTION;
        }
    }

    private static Difficulty parseDifficulty(String difficulty) {
        try {
            return difficulty == null ? Difficulty.MEDIUM : Difficulty.valueOf(difficulty.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Difficulty.MEDIUM;
        }
    }

    private static BigDecimal toConfidence(Double confidence) {
        if (confidence == null || confidence.isNaN()) {
            return null;
        }
        double clamped = Math.max(0, Math.min(1, confidence));
        return BigDecimal.valueOf(clamped).setScale(3, RoundingMode.HALF_UP);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1).strip() + "…";
    }
}
