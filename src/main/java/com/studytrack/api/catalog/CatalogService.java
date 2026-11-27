package com.studytrack.api.catalog;

import com.studytrack.api.catalog.CatalogDtos.CatalogSubjectResponse;
import com.studytrack.api.catalog.CatalogDtos.CatalogTopicNodeResponse;
import com.studytrack.api.catalog.CatalogDtos.CatalogTrackResponse;
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
    private final CatalogTrackRepository catalogTrackRepository;
    private final CatalogSubjectRepository catalogSubjectRepository;
    private final CatalogTopicRepository catalogTopicRepository;
    private final SubjectService subjectService;
    private final TopicService topicService;

    public CatalogService(CatalogTrackRepository catalogTrackRepository,
                          CatalogSubjectRepository catalogSubjectRepository,
                          CatalogTopicRepository catalogTopicRepository, SubjectService subjectService,
                          TopicService topicService) {
        this.catalogTrackRepository = catalogTrackRepository;
        this.catalogSubjectRepository = catalogSubjectRepository;
        this.catalogTopicRepository = catalogTopicRepository;
        this.subjectService = subjectService;
        this.topicService = topicService;
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
        Map<Long, Long> copiedSubjectIds = subjectService.mapCopiedSubjects(subjectIds);
        return subjects.stream()
                .map(subject -> new CatalogSubjectResponse(subject.getId(), subject.getTrackId(), subject.getName(),
                        subject.getDescription(), subject.getIcon(), topicCounts.getOrDefault(subject.getId(), 0L),
                        copiedSubjectIds.get(subject.getId())))
                .toList();
    }

    /** Returns the subject's topics as a tree; siblings are ordered by sort order, then id. */
    @Transactional(readOnly = true)
    public List<CatalogTopicNodeResponse> listSubjectTopics(Long catalogSubjectId) {
        findCatalogSubject(catalogSubjectId);
        return buildNodes(readTopicsByParent(catalogSubjectId), 0L);
    }

    /**
     * Copies a catalog subject and its whole topic tree into the current user's subjects. The user's copy is
     * independent: later catalog changes do not touch it. Fails with 409 when the user already added it.
     */
    @Transactional
    public SubjectResponse enrollInSubject(Long catalogSubjectId) {
        CatalogSubjectEntity catalogSubject = findCatalogSubject(catalogSubjectId);
        SubjectResponse subject = subjectService.createSubjectFromCatalog(catalogSubject.getId(),
                catalogSubject.getName(), catalogSubject.getDescription(), catalogSubject.getIcon());
        topicService.copyTopicTree(subject.id(), buildTemplates(readTopicsByParent(catalogSubjectId), 0L));
        return subjectService.getSubject(subject.id());
    }

    private CatalogSubjectEntity findCatalogSubject(Long catalogSubjectId) {
        return catalogSubjectRepository.findById(catalogSubjectId)
                .orElseThrow(() -> new NoSuchElementException("Catalog subject " + catalogSubjectId + " not found"));
    }

    private Map<Long, List<CatalogTopicEntity>> readTopicsByParent(Long catalogSubjectId) {
        return catalogTopicRepository.findByCatalogSubjectIdOrderBySortOrderAscIdAsc(catalogSubjectId).stream()
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
                .map(topic -> new TopicTemplate(topic.getName(), buildTemplates(topicsByParent, topic.getId())))
                .toList();
    }
}
