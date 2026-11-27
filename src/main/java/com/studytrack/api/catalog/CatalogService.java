package com.studytrack.api.catalog;

import com.studytrack.api.catalog.CatalogDtos.CatalogSubjectResponse;
import com.studytrack.api.catalog.CatalogDtos.CatalogTopicNodeResponse;
import com.studytrack.api.catalog.CatalogDtos.CatalogTrackResponse;
import com.studytrack.api.item.ItemContent;
import com.studytrack.api.item.ItemService;
import com.studytrack.api.item.ItemService.CatalogQuestionTemplate;
import com.studytrack.api.subject.SubjectDtos.SubjectResponse;
import com.studytrack.api.subject.SubjectService;
import com.studytrack.api.topic.TopicService;
import com.studytrack.api.topic.TopicService.TopicTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

/** Browsing the shared catalog and copying one of its subjects into the current user's subjects. */
@Service
public class CatalogService {
    private static final int MAX_TITLE_LENGTH = 300;

    private final CatalogTrackRepository catalogTrackRepository;
    private final CatalogSubjectRepository catalogSubjectRepository;
    private final CatalogTopicRepository catalogTopicRepository;
    private final CatalogQuestionRepository catalogQuestionRepository;
    private final SubjectService subjectService;
    private final TopicService topicService;
    private final ItemService itemService;

    public CatalogService(CatalogTrackRepository catalogTrackRepository,
                          CatalogSubjectRepository catalogSubjectRepository,
                          CatalogTopicRepository catalogTopicRepository,
                          CatalogQuestionRepository catalogQuestionRepository, SubjectService subjectService,
                          TopicService topicService, ItemService itemService) {
        this.catalogTrackRepository = catalogTrackRepository;
        this.catalogSubjectRepository = catalogSubjectRepository;
        this.catalogTopicRepository = catalogTopicRepository;
        this.catalogQuestionRepository = catalogQuestionRepository;
        this.subjectService = subjectService;
        this.topicService = topicService;
        this.itemService = itemService;
    }

    /** One catalog question as the user's copy of it reads: the subject, the topic and the question's plain text. */
    public record CatalogQuestionRef(String subjectName, String topicName, String questionText) {
    }

    /**
     * Every catalog question, by track, subject, topic and position. The text is what
     * {@link #enrollInSubject} gives the user's copy once it is stored as HTML and read back as text, so an
     * explanation saved for a reference here is found by the copy.
     */
    @Transactional(readOnly = true)
    public List<CatalogQuestionRef> listCatalogQuestions() {
        Map<Long, CatalogTopicEntity> topicsById = catalogTopicRepository.findAll().stream()
                .collect(Collectors.toMap(CatalogTopicEntity::getId, topic -> topic));
        Map<Long, CatalogSubjectEntity> subjectsById = catalogSubjectRepository.findAll().stream()
                .collect(Collectors.toMap(CatalogSubjectEntity::getId, subject -> subject));
        Map<Long, Integer> trackOrderById = catalogTrackRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .collect(Collectors.toMap(CatalogTrackEntity::getId, CatalogTrackEntity::getSortOrder));
        return catalogQuestionRepository.findAll().stream()
                .sorted(java.util.Comparator
                        .comparing((CatalogQuestionEntity question) -> trackOrderOf(question, topicsById, subjectsById, trackOrderById))
                        .thenComparing(question -> subjectsById.get(topicsById.get(question.getCatalogTopicId()).getCatalogSubjectId()).getSortOrder())
                        .thenComparing(question -> topicsById.get(question.getCatalogTopicId()).getSortOrder())
                        .thenComparing(CatalogQuestionEntity::getSortOrder)
                        .thenComparing(CatalogQuestionEntity::getId))
                .map(question -> toQuestionRef(question, topicsById, subjectsById))
                .toList();
    }

    private static int trackOrderOf(CatalogQuestionEntity question, Map<Long, CatalogTopicEntity> topicsById,
                                    Map<Long, CatalogSubjectEntity> subjectsById, Map<Long, Integer> trackOrderById) {
        CatalogSubjectEntity subject = subjectsById.get(topicsById.get(question.getCatalogTopicId()).getCatalogSubjectId());
        return trackOrderById.getOrDefault(subject.getTrackId(), 0);
    }

    private static CatalogQuestionRef toQuestionRef(CatalogQuestionEntity question,
                                                    Map<Long, CatalogTopicEntity> topicsById,
                                                    Map<Long, CatalogSubjectEntity> subjectsById) {
        CatalogTopicEntity topic = topicsById.get(question.getCatalogTopicId());
        CatalogSubjectEntity subject = subjectsById.get(topic.getCatalogSubjectId());
        String text = ItemContent.toPlainText(CatalogAnswerFormatter.formatToHtml(question.getQuestion()));
        return new CatalogQuestionRef(subject.getName(), topic.getName(), text != null ? text : question.getQuestion());
    }

    @Transactional(readOnly = true)
    public List<CatalogTrackResponse> listTracks() {
        List<CatalogTrackEntity> tracks = catalogTrackRepository.findAllByOrderBySortOrderAscNameAsc();
        if (tracks.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> subjectCounts = catalogTrackRepository
                .countSubjectsByTrack(tracks.stream().map(CatalogTrackEntity::getId).toList()).stream()
                .collect(Collectors.toMap(CatalogTrackRepository.TrackSubjectCount::getTrackId,
                        CatalogTrackRepository.TrackSubjectCount::getSubjectCount));
        return tracks.stream()
                .map(track -> new CatalogTrackResponse(track.getId(), track.getName(), track.getDescription(),
                        subjectCounts.getOrDefault(track.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogSubjectResponse> listTrackSubjects(Long trackId) {
        if (!catalogTrackRepository.existsById(trackId)) {
            throw new NoSuchElementException("Track " + trackId + " not found");
        }
        List<CatalogSubjectEntity> subjects = catalogSubjectRepository.findByTrackIdOrderBySortOrderAscNameAsc(trackId);
        List<Long> subjectIds = subjects.stream().map(CatalogSubjectEntity::getId).toList();
        Map<Long, Long> topicCounts = subjectIds.isEmpty() ? Map.of()
                : catalogSubjectRepository.countTopicsBySubject(subjectIds).stream()
                .collect(Collectors.toMap(CatalogSubjectRepository.SubjectTopicCount::getCatalogSubjectId,
                        CatalogSubjectRepository.SubjectTopicCount::getTopicCount));
        Map<Long, Long> questionCounts = subjectIds.isEmpty() ? Map.of()
                : catalogSubjectRepository.countQuestionsBySubject(subjectIds).stream()
                .collect(Collectors.toMap(CatalogSubjectRepository.SubjectQuestionCount::getCatalogSubjectId,
                        CatalogSubjectRepository.SubjectQuestionCount::getQuestionCount));
        Map<Long, Long> copiedSubjectIds = subjectService.mapCopiedSubjects(subjectIds);
        return subjects.stream()
                .map(subject -> new CatalogSubjectResponse(subject.getId(), subject.getTrackId(), subject.getName(),
                        subject.getDescription(), subject.getIcon(), topicCounts.getOrDefault(subject.getId(), 0L),
                        questionCounts.getOrDefault(subject.getId(), 0L), copiedSubjectIds.get(subject.getId())))
                .toList();
    }

    /** Returns the subject's topics as a tree; siblings are ordered by sort order, then id. */
    @Transactional(readOnly = true)
    public List<CatalogTopicNodeResponse> listSubjectTopics(Long catalogSubjectId) {
        findCatalogSubject(catalogSubjectId);
        return buildNodes(readTopicsByParent(catalogSubjectId), 0L);
    }

    /**
     * Copies a catalog subject, its whole topic tree and its starter questions into the current user's subjects.
     * The user's copy is independent: later catalog changes do not touch it. Fails with 409 when the user
     * already added it.
     */
    @Transactional
    public SubjectResponse enrollInSubject(Long catalogSubjectId) {
        CatalogSubjectEntity catalogSubject = findCatalogSubject(catalogSubjectId);
        SubjectResponse subject = subjectService.createSubjectFromCatalog(catalogSubject.getId(),
                catalogSubject.getName(), catalogSubject.getDescription(), catalogSubject.getIcon());
        List<CatalogTopicEntity> catalogTopics =
                catalogTopicRepository.findByCatalogSubjectIdOrderBySortOrderAscIdAsc(catalogSubjectId);
        Map<Long, Long> topicIdsByCatalogTopicId =
                topicService.copyTopicTree(subject.id(), buildTemplates(groupByParent(catalogTopics), 0L));
        copyQuestions(catalogTopics, topicIdsByCatalogTopicId);
        return subjectService.getSubject(subject.id());
    }

    private void copyQuestions(List<CatalogTopicEntity> catalogTopics, Map<Long, Long> topicIdsByCatalogTopicId) {
        if (catalogTopics.isEmpty()) {
            return;
        }
        Map<Long, List<CatalogQuestionEntity>> questionsByTopicId = catalogQuestionRepository
                .findByCatalogTopicIdInOrderBySortOrderAscIdAsc(
                        catalogTopics.stream().map(CatalogTopicEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(CatalogQuestionEntity::getCatalogTopicId));
        questionsByTopicId.forEach((catalogTopicId, questions) -> itemService.createCatalogQuestions(
                topicIdsByCatalogTopicId.get(catalogTopicId), questions.stream().map(this::toQuestionTemplate).toList()));
    }

    private CatalogQuestionTemplate toQuestionTemplate(CatalogQuestionEntity question) {
        String title = question.getQuestion().length() > MAX_TITLE_LENGTH
                ? question.getQuestion().substring(0, MAX_TITLE_LENGTH)
                : question.getQuestion();
        return new CatalogQuestionTemplate(title, CatalogAnswerFormatter.formatToHtml(question.getQuestion()),
                CatalogAnswerFormatter.formatToHtml(question.getAnswer()), question.getDifficulty());
    }

    private CatalogSubjectEntity findCatalogSubject(Long catalogSubjectId) {
        return catalogSubjectRepository.findById(catalogSubjectId)
                .orElseThrow(() -> new NoSuchElementException("Catalog subject " + catalogSubjectId + " not found"));
    }

    private Map<Long, List<CatalogTopicEntity>> readTopicsByParent(Long catalogSubjectId) {
        return groupByParent(catalogTopicRepository.findByCatalogSubjectIdOrderBySortOrderAscIdAsc(catalogSubjectId));
    }

    private static Map<Long, List<CatalogTopicEntity>> groupByParent(List<CatalogTopicEntity> topics) {
        return topics.stream()
                .collect(Collectors.groupingBy(topic -> Objects.requireNonNullElse(topic.getParentId(), 0L)));
    }

    private List<CatalogTopicNodeResponse> buildNodes(Map<Long, List<CatalogTopicEntity>> topicsByParent,
                                                      Long parentKey) {
        List<CatalogTopicNodeResponse> nodes = new ArrayList<>();
        for (CatalogTopicEntity topic : topicsByParent.getOrDefault(parentKey, List.of())) {
            nodes.add(new CatalogTopicNodeResponse(topic.getId(), topic.getName(),
                    buildNodes(topicsByParent, topic.getId())));
        }
        return nodes;
    }

    private List<TopicTemplate> buildTemplates(Map<Long, List<CatalogTopicEntity>> topicsByParent, Long parentKey) {
        return topicsByParent.getOrDefault(parentKey, List.of()).stream()
                .map(topic -> new TopicTemplate(topic.getId(), topic.getName(), buildTemplates(topicsByParent, topic.getId())))
                .toList();
    }
}
