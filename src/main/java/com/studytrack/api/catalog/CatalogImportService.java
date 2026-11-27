package com.studytrack.api.catalog;

import com.studytrack.api.catalog.CatalogCsvReader.CatalogCsvRow;
import com.studytrack.api.catalog.CatalogDtos.CatalogImportResponse;
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
    private static final int MAX_REJECTED_MESSAGES = 20;

    private final CatalogCsvReader catalogCsvReader;
    private final CatalogTrackRepository catalogTrackRepository;
    private final CatalogSubjectRepository catalogSubjectRepository;
    private final CatalogTopicRepository catalogTopicRepository;

    public CatalogImportService(CatalogCsvReader catalogCsvReader, CatalogTrackRepository catalogTrackRepository,
                                CatalogSubjectRepository catalogSubjectRepository,
                                CatalogTopicRepository catalogTopicRepository) {
        this.catalogCsvReader = catalogCsvReader;
        this.catalogTrackRepository = catalogTrackRepository;
        this.catalogSubjectRepository = catalogSubjectRepository;
        this.catalogTopicRepository = catalogTopicRepository;
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
                run.addTopicPath(subject, splitTopicPath(row.topicPath()));
            }
        }
        return run.toResponse(rows.size());
    }

    private static String findRowProblem(CatalogCsvRow row) {
        if (row.track() == null || row.subject() == null) {
            return "needs a track and a subject";
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

    private static String keyOf(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** State of one import: what exists (loaded once per track or subject) and what was added. */
    private final class ImportRun {
        private final Map<String, CatalogTrackEntity> tracksByName = new HashMap<>();
        private final Map<Long, List<CatalogSubjectEntity>> subjectsByTrackId = new HashMap<>();
        private final Map<Long, List<CatalogTopicEntity>> topicsBySubjectId = new HashMap<>();
        private final List<String> rejectedMessages = new ArrayList<>();
        private int rejectedRowCount;
        private int tracksAdded;
        private int subjectsAdded;
        private int topicsAdded;

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

        void addTopicPath(CatalogSubjectEntity subject, List<String> names) {
            List<CatalogTopicEntity> topics = topicsBySubjectId.computeIfAbsent(subject.getId(),
                    subjectId -> new ArrayList<>(
                            catalogTopicRepository.findByCatalogSubjectIdOrderBySortOrderAscIdAsc(subjectId)));
            Long parentId = null;
            for (String name : names) {
                CatalogTopicEntity topic = findOrCreateTopic(subject.getId(), topics, parentId, name);
                parentId = topic.getId();
            }
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
            return new CatalogImportResponse(rowsRead, tracksAdded, subjectsAdded, topicsAdded, rejectedRowCount,
                    List.copyOf(rejectedMessages));
        }
    }
}
