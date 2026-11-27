package com.studytrack.api.catalog;

import com.studytrack.api.catalog.CatalogCsvReader.CatalogCsvRow;
import com.studytrack.api.catalog.CatalogDtos.CatalogImportResponse;
import com.studytrack.api.item.Difficulty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Adds tracks, subjects and topics from a catalog CSV. Additive and repeatable: rows that already exist
 * (matched by name, ignoring case) are left alone, nothing is ever deleted. Callers check who may import;
 * this class does not.
 */
@Service
public class CatalogImportService {
    private static final String TOPIC_PATH_SEPARATOR = ">";
    private static final int MAX_TRACK_NAME_LENGTH = 120;
    private static final int MAX_SUBJECT_NAME_LENGTH = 120;
    private static final int MAX_TOPIC_NAME_LENGTH = 200;
    private static final int MAX_QUESTION_LENGTH = 1000;
    private static final int MAX_ANSWER_LENGTH = 20000;
    private static final int MAX_REJECTED_MESSAGES = 20;

    private final CatalogCsvReader catalogCsvReader;
    private final CatalogTrackRepository catalogTrackRepository;
    private final CatalogSubjectRepository catalogSubjectRepository;
    private final CatalogTopicRepository catalogTopicRepository;
    private final CatalogQuestionRepository catalogQuestionRepository;

    public CatalogImportService(CatalogCsvReader catalogCsvReader, CatalogTrackRepository catalogTrackRepository,
                                CatalogSubjectRepository catalogSubjectRepository,
                                CatalogTopicRepository catalogTopicRepository,
                                CatalogQuestionRepository catalogQuestionRepository) {
        this.catalogCsvReader = catalogCsvReader;
        this.catalogTrackRepository = catalogTrackRepository;
        this.catalogSubjectRepository = catalogSubjectRepository;
        this.catalogTopicRepository = catalogTopicRepository;
        this.catalogQuestionRepository = catalogQuestionRepository;
    }

    /** Imports every usable row in one transaction; unusable rows are reported, not fatal. */
    @Transactional
    public CatalogImportResponse importCatalogCsv(byte[] content) {
        List<CatalogCsvRow> rows = catalogCsvReader.readRows(content);
        ImportRun run = new ImportRun();
        for (CatalogCsvRow row : rows) {
            String problem = findRowProblem(row);
            if (problem != null) {
                run.rejectRow(row.lineNumber(), problem);
                continue;
            }
            CatalogTrackEntity track = run.findOrCreateTrack(row.track());
            CatalogSubjectEntity subject = run.findOrCreateSubject(track, row.subject());
            if (row.topicPath() != null) {
                CatalogTopicEntity topic = run.addTopicPath(subject, splitTopicPath(row.topicPath()));
                if (row.question() != null && topic != null) {
                    run.addQuestion(topic, row);
                }
            }
        }
        return run.toResponse(rows.size());
    }

    private static String findRowProblem(CatalogCsvRow row) {
        if (row.track() == null || row.subject() == null) {
            return "needs a track and a subject";
        }
        if (row.question() != null && (row.topicPath() == null || splitTopicPath(row.topicPath()).isEmpty())) {
            return "a question needs a topic_path";
        }
        if (row.question() != null && row.question().length() > MAX_QUESTION_LENGTH) {
            return "the question is longer than " + MAX_QUESTION_LENGTH + " characters";
        }
        if (row.answer() != null && row.answer().length() > MAX_ANSWER_LENGTH) {
            return "the answer is longer than " + MAX_ANSWER_LENGTH + " characters";
        }
        if (row.difficulty() != null && parseDifficulty(row.difficulty()) == null) {
            return "difficulty must be EASY, MEDIUM or HARD";
        }
        if (row.track().length() > MAX_TRACK_NAME_LENGTH || row.subject().length() > MAX_SUBJECT_NAME_LENGTH) {
            return "track or subject name is longer than " + MAX_SUBJECT_NAME_LENGTH + " characters";
        }
        if (row.topicPath() != null && splitTopicPath(row.topicPath()).stream()
                .anyMatch(name -> name.length() > MAX_TOPIC_NAME_LENGTH)) {
            return "a topic name is longer than " + MAX_TOPIC_NAME_LENGTH + " characters";
        }
        return null;
    }

    private static List<String> splitTopicPath(String topicPath) {
        return Arrays.stream(topicPath.split(TOPIC_PATH_SEPARATOR))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    /** The difficulty named by the text (ignoring case), or null when it is not one of them. */
    private static Difficulty parseDifficulty(String text) {
        try {
            return Difficulty.valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String keyOf(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String normalizeSpaces(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }

    /** State of one import: what exists (loaded once per track or subject) and what was added. */
    private final class ImportRun {
        private final Map<String, CatalogTrackEntity> tracksByName = new HashMap<>();
        private final Map<Long, List<CatalogSubjectEntity>> subjectsByTrackId = new HashMap<>();
        private final Map<Long, List<CatalogTopicEntity>> topicsBySubjectId = new HashMap<>();
        private final Map<Long, List<CatalogQuestionEntity>> questionsByTopicId = new HashMap<>();
        private final List<String> rejectedMessages = new ArrayList<>();
        private int rejectedRowCount;
        private int tracksAdded;
        private int subjectsAdded;
        private int topicsAdded;
        private int questionsAdded;

        ImportRun() {
            for (CatalogTrackEntity track : catalogTrackRepository.findAllByOrderBySortOrderAscNameAsc()) {
                tracksByName.put(keyOf(track.getName()), track);
            }
        }

        void rejectRow(int lineNumber, String problem) {
            rejectedRowCount++;
            if (rejectedMessages.size() < MAX_REJECTED_MESSAGES) {
                rejectedMessages.add("Line " + lineNumber + ": " + problem);
            }
        }

        CatalogTrackEntity findOrCreateTrack(String name) {
            CatalogTrackEntity existing = tracksByName.get(keyOf(name));
            if (existing != null) {
                return existing;
            }
            int sortOrder = tracksByName.values().stream().mapToInt(CatalogTrackEntity::getSortOrder).max().orElse(-1) + 1;
            CatalogTrackEntity created = catalogTrackRepository.save(new CatalogTrackEntity(name, sortOrder));
            tracksByName.put(keyOf(name), created);
            subjectsByTrackId.put(created.getId(), new ArrayList<>());
            tracksAdded++;
            return created;
        }

        CatalogSubjectEntity findOrCreateSubject(CatalogTrackEntity track, String name) {
            List<CatalogSubjectEntity> subjects = subjectsByTrackId.computeIfAbsent(track.getId(),
                    trackId -> new ArrayList<>(catalogSubjectRepository.findByTrackIdOrderBySortOrderAscNameAsc(trackId)));
            for (CatalogSubjectEntity subject : subjects) {
                if (subject.getName().equalsIgnoreCase(name)) {
                    return subject;
                }
            }
            int sortOrder = subjects.stream().mapToInt(CatalogSubjectEntity::getSortOrder).max().orElse(-1) + 1;
            CatalogSubjectEntity created = catalogSubjectRepository.save(
                    new CatalogSubjectEntity(track.getId(), name, sortOrder));
            subjects.add(created);
            topicsBySubjectId.put(created.getId(), new ArrayList<>());
            subjectsAdded++;
            return created;
        }

        /** Creates what is missing of the path and returns its last topic, or null for an empty path. */
        CatalogTopicEntity addTopicPath(CatalogSubjectEntity subject, List<String> names) {
            List<CatalogTopicEntity> topics = topicsBySubjectId.computeIfAbsent(subject.getId(),
                    subjectId -> new ArrayList<>(
                            catalogTopicRepository.findByCatalogSubjectIdOrderBySortOrderAscIdAsc(subjectId)));
            CatalogTopicEntity topic = null;
            for (String name : names) {
                topic = findOrCreateTopic(subject.getId(), topics, topic == null ? null : topic.getId(), name);
            }
            return topic;
        }

        void addQuestion(CatalogTopicEntity topic, CatalogCsvRow row) {
            List<CatalogQuestionEntity> questions = questionsByTopicId.computeIfAbsent(topic.getId(),
                    topicId -> new ArrayList<>(
                            catalogQuestionRepository.findByCatalogTopicIdOrderBySortOrderAscIdAsc(topicId)));
            String normalizedQuestion = normalizeSpaces(row.question());
            int nextSortOrder = 0;
            for (CatalogQuestionEntity existing : questions) {
                if (normalizeSpaces(existing.getQuestion()).equalsIgnoreCase(normalizedQuestion)) {
                    return;
                }
                nextSortOrder = Math.max(nextSortOrder, existing.getSortOrder() + 1);
            }
            Difficulty difficulty = row.difficulty() == null ? Difficulty.MEDIUM : parseDifficulty(row.difficulty());
            questions.add(catalogQuestionRepository.save(new CatalogQuestionEntity(topic.getId(), row.question(),
                    row.answer(), difficulty, nextSortOrder)));
            questionsAdded++;
        }

        private CatalogTopicEntity findOrCreateTopic(Long subjectId, List<CatalogTopicEntity> topics, Long parentId,
                                                     String name) {
            int nextSortOrder = 0;
            for (CatalogTopicEntity topic : topics) {
                if (!Objects.equals(topic.getParentId(), parentId)) {
                    continue;
                }
                if (topic.getName().equalsIgnoreCase(name)) {
                    return topic;
                }
                nextSortOrder = Math.max(nextSortOrder, topic.getSortOrder() + 1);
            }
            CatalogTopicEntity created = catalogTopicRepository.save(
                    new CatalogTopicEntity(subjectId, parentId, name, nextSortOrder));
            topics.add(created);
            topicsAdded++;
            return created;
        }

        CatalogImportResponse toResponse(int rowsRead) {
            return new CatalogImportResponse(rowsRead, tracksAdded, subjectsAdded, topicsAdded, questionsAdded,
                    rejectedRowCount, List.copyOf(rejectedMessages));
        }
    }
}
