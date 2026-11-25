package com.studytrack.api.topic;

import com.studytrack.api.content.HtmlSanitizer;
import com.studytrack.api.subject.SubjectEntity;
import com.studytrack.api.subject.SubjectService;
import com.studytrack.api.topic.TopicDtos.CreateTopicRequest;
import com.studytrack.api.topic.TopicDtos.MoveTopicRequest;
import com.studytrack.api.topic.TopicDtos.ReplaceTopicRequest;
import com.studytrack.api.topic.TopicDtos.TopicNodeResponse;
import com.studytrack.api.topic.TopicDtos.TopicResponse;
import com.studytrack.api.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TopicService {
    private static final int MAX_TOPIC_NAME_LENGTH = 200;

    private final TopicRepository topicRepository;
    private final SubjectService subjectService;
    private final HtmlSanitizer htmlSanitizer;
    private final CurrentUser currentUser;

    public TopicService(TopicRepository topicRepository, SubjectService subjectService, HtmlSanitizer htmlSanitizer,
                        CurrentUser currentUser) {
        this.topicRepository = topicRepository;
        this.subjectService = subjectService;
        this.htmlSanitizer = htmlSanitizer;
        this.currentUser = currentUser;
    }

    /** Returns the subject's topics as a tree; siblings are ordered by sort order, then id. */
    @Transactional(readOnly = true)
    public List<TopicNodeResponse> listTopicTree(Long subjectId) {
        SubjectEntity subject = subjectService.findOwnedSubject(subjectId);
        List<TopicEntity> topics = topicRepository.findBySubjectIdOrderBySortOrderAscIdAsc(subject.getId());
        long userId = currentUser.getUserId();
        Map<Long, TopicRepository.TopicItemCount> itemCountsByTopic =
                topicRepository.countItemsByTopic(subject.getId(), userId).stream()
                        .collect(Collectors.toMap(TopicRepository.TopicItemCount::getTopicId, Function.identity()));
        Set<Long> completedTopicIds = new HashSet<>(topicRepository.findCompletedTopicIds(subject.getId(), userId));
        Map<Long, List<TopicEntity>> topicsByParent = topics.stream()
                .collect(Collectors.groupingBy(topic -> Objects.requireNonNullElse(topic.getParentId(), 0L)));
        return buildNodes(0L, topicsByParent, new TreeCounts(itemCountsByTopic, completedTopicIds));
    }

    @Transactional(readOnly = true)
    public TopicResponse getTopic(Long topicId) {
        return toResponse(findOwnedTopic(topicId));
    }

    @Transactional
    public TopicResponse createTopic(CreateTopicRequest request) {
        SubjectEntity subject = subjectService.findOwnedSubject(request.subjectId());
        if (request.parentId() != null) {
            requireTopicInSubject(request.parentId(), subject.getId());
        }
        TopicEntity topic = new TopicEntity(subject.getId());
        topic.setParentId(request.parentId());
        topic.setName(request.name().strip());
        topic.setDescriptionHtml(htmlSanitizer.sanitizeHtml(request.descriptionHtml()));
        topic.setSortOrder(request.sortOrder() != null
                ? request.sortOrder()
                : topicRepository.findMaxSortOrder(subject.getId(), request.parentId()) + 1);
        return toResponse(topicRepository.save(topic));
    }

    @Transactional
    public TopicResponse replaceTopic(Long topicId, ReplaceTopicRequest request) {
        TopicEntity topic = findOwnedTopic(topicId);
        topic.setName(request.name().strip());
        topic.setDescriptionHtml(htmlSanitizer.sanitizeHtml(request.descriptionHtml()));
        if (request.sortOrder() != null) {
            topic.setSortOrder(request.sortOrder());
        }
        return toResponse(topicRepository.saveAndFlush(topic));
    }

    /** Moves a topic under another topic of the same subject (or to the top level); never under itself or a descendant. */
    @Transactional
    public TopicResponse moveTopic(Long topicId, MoveTopicRequest request) {
        TopicEntity topic = findOwnedTopic(topicId);
        Long newParentId = request.parentId();
        if (newParentId != null) {
            requireTopicInSubject(newParentId, topic.getSubjectId());
            if (isSelfOrDescendant(newParentId, topic)) {
                throw new IllegalArgumentException("A topic cannot be moved under itself or one of its subtopics");
            }
        }
        topic.setParentId(newParentId);
        topic.setSortOrder(request.sortOrder());
        return toResponse(topicRepository.saveAndFlush(topic));
    }

    /** Deletes the topic with its subtopics and all their items (database cascade). */
    @Transactional
    public void deleteTopic(Long topicId) {
        topicRepository.delete(findOwnedTopic(topicId));
    }

    /** The topic at the end of a path and how many topics had to be created for it. */
    public record TopicPathResult(TopicEntity topic, int createdTopicCount) {
    }

    /**
     * Walks {@code names} ("Spring Boot", "Security") down from the subject's top level, matching
     * existing topics by name (case-insensitive) and creating missing ones after their siblings.
     */
    @Transactional
    public TopicPathResult findOrCreateTopicPath(Long subjectId, List<String> names) {
        if (names.isEmpty()) {
            throw new IllegalArgumentException("A topic path needs at least one name");
        }
        SubjectEntity subject = subjectService.findOwnedSubject(subjectId);
        List<TopicEntity> topics = new ArrayList<>(topicRepository.findBySubjectIdOrderBySortOrderAscIdAsc(subject.getId()));
        TopicEntity current = null;
        int createdCount = 0;
        for (String rawName : names) {
            String name = rawName.strip();
            String topicName = name.length() > MAX_TOPIC_NAME_LENGTH ? name.substring(0, MAX_TOPIC_NAME_LENGTH) : name;
            Long parentId = current == null ? null : current.getId();
            Optional<TopicEntity> existing = topics.stream()
                    .filter(topic -> Objects.equals(topic.getParentId(), parentId))
                    .filter(topic -> topic.getName().equalsIgnoreCase(topicName))
                    .findFirst();
            if (existing.isPresent()) {
                current = existing.get();
                continue;
            }
            TopicEntity created = new TopicEntity(subject.getId());
            created.setParentId(parentId);
            created.setName(topicName);
            created.setSortOrder(topicRepository.findMaxSortOrder(subject.getId(), parentId) + 1);
            current = topicRepository.saveAndFlush(created);
            topics.add(current);
            createdCount++;
        }
        return new TopicPathResult(current, createdCount);
    }

    /** Returns the topic if its subject belongs to the current user; other users' topics are reported as missing. */
    @Transactional(readOnly = true)
    public TopicEntity findOwnedTopic(Long topicId) {
        return topicRepository.findOwnedTopic(topicId, currentUser.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Topic " + topicId + " not found"));
    }

    private void requireTopicInSubject(Long topicId, Long subjectId) {
        TopicEntity parent = findOwnedTopic(topicId);
        if (!parent.getSubjectId().equals(subjectId)) {
            throw new IllegalArgumentException("Parent topic " + topicId + " belongs to another subject");
        }
    }

    private boolean isSelfOrDescendant(Long candidateId, TopicEntity topic) {
        Map<Long, Long> parentById = topicRepository.findBySubjectIdOrderBySortOrderAscIdAsc(topic.getSubjectId())
                .stream()
                .filter(each -> each.getParentId() != null)
                .collect(Collectors.toMap(TopicEntity::getId, TopicEntity::getParentId));
        Long current = candidateId;
        while (current != null) {
            if (current.equals(topic.getId())) {
                return true;
            }
            current = parentById.get(current);
        }
        return false;
    }

    private List<TopicNodeResponse> buildNodes(Long parentKey, Map<Long, List<TopicEntity>> topicsByParent,
                                               TreeCounts counts) {
        List<TopicNodeResponse> nodes = new ArrayList<>();
        for (TopicEntity topic : topicsByParent.getOrDefault(parentKey, List.of())) {
            List<TopicNodeResponse> children = buildNodes(topic.getId(), topicsByParent, counts);
            TopicRepository.TopicItemCount own = counts.itemCounts().get(topic.getId());
            long itemCount = own == null ? 0 : own.getItemCount();
            long doneCount = own == null ? 0 : own.getDoneCount();
            long totalItemCount = itemCount + children.stream().mapToLong(TopicNodeResponse::totalItemCount).sum();
            long totalDoneCount = doneCount + children.stream().mapToLong(TopicNodeResponse::totalDoneCount).sum();
            nodes.add(new TopicNodeResponse(topic.getId(), topic.getParentId(), topic.getName(),
                    topic.getDescriptionHtml(), topic.getSortOrder(), itemCount, totalItemCount, doneCount,
                    totalDoneCount, SubjectService.percentOf(totalDoneCount, totalItemCount),
                    counts.completedTopicIds().contains(topic.getId()),
                    totalItemCount > 0 && totalDoneCount == totalItemCount, children));
        }
        return nodes;
    }

    private record TreeCounts(Map<Long, TopicRepository.TopicItemCount> itemCounts, Set<Long> completedTopicIds) {
    }

    private TopicResponse toResponse(TopicEntity topic) {
        return new TopicResponse(topic.getId(), topic.getSubjectId(), topic.getParentId(), topic.getName(),
                topic.getDescriptionHtml(), topic.getSortOrder(), topic.getCreatedAt(), topic.getUpdatedAt());
    }
}
