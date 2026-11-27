package com.studytrack.api.item;

import com.studytrack.api.content.HtmlSanitizer;
import com.studytrack.api.item.ItemDtos.DuplicateCheckRequest;
import com.studytrack.api.item.ItemDtos.DuplicateItemResponse;
import com.studytrack.api.item.ItemDtos.ItemPageResponse;
import com.studytrack.api.item.ItemDtos.ItemProgressResponse;
import com.studytrack.api.item.ItemDtos.ItemRequest;
import com.studytrack.api.item.ItemDtos.ItemResponse;
import com.studytrack.api.item.ItemDtos.ItemSummaryResponse;
import com.studytrack.api.media.MediaService;
import com.studytrack.api.subject.SubjectEntity;
import com.studytrack.api.subject.SubjectService;
import com.studytrack.api.topic.TopicEntity;
import com.studytrack.api.topic.TopicService;
import com.studytrack.api.user.CurrentUser;
import java.util.HashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ItemService {
    private static final int MAX_DUPLICATES = 5;

    private final StudyItemRepository studyItemRepository;
    private final TagRepository tagRepository;
    private final TopicService topicService;
    private final SubjectService subjectService;
    private final MediaService mediaService;
    private final HtmlSanitizer htmlSanitizer;
    private final CurrentUser currentUser;

    public ItemService(StudyItemRepository studyItemRepository, TagRepository tagRepository,
                       TopicService topicService, SubjectService subjectService, MediaService mediaService,
                       HtmlSanitizer htmlSanitizer, CurrentUser currentUser) {
        this.studyItemRepository = studyItemRepository;
        this.tagRepository = tagRepository;
        this.topicService = topicService;
        this.subjectService = subjectService;
        this.mediaService = mediaService;
        this.htmlSanitizer = htmlSanitizer;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public ItemPageResponse listTopicItems(Long topicId, Difficulty difficulty, String tag, ItemStatus status,
                                          int page, int size) {
        TopicEntity topic = topicService.findOwnedTopic(topicId);
        String tagName = tag == null || tag.isBlank() ? null : normalizeTag(tag);
        long userId = currentUser.getUserId();
        Page<StudyItemEntity> items = studyItemRepository.findTopicItems(topic.getId(), difficulty, tagName, status,
                status == ItemStatus.NOT_STARTED, userId, PageRequest.of(page, size));
        return toPage(items, page, size);
    }

    /** One page of the items of all the subject's topics, ordered by topic, then item. */
    @Transactional(readOnly = true)
    public ItemPageResponse listSubjectItems(Long subjectId, Difficulty difficulty, String tag, ItemStatus status,
                                            int page, int size) {
        SubjectEntity subject = subjectService.findOwnedSubject(subjectId);
        String tagName = tag == null || tag.isBlank() ? null : normalizeTag(tag);
        Page<StudyItemEntity> items = studyItemRepository.findSubjectItems(subject.getId(), difficulty, tagName, status,
                status == ItemStatus.NOT_STARTED, currentUser.getUserId(), PageRequest.of(page, size));
        return toPage(items, page, size);
    }

    private ItemPageResponse toPage(Page<StudyItemEntity> items, int page, int size) {
        Map<Long, ItemProgressResponse> progressByItem = findProgress(
                items.getContent().stream().map(StudyItemEntity::getId).toList());
        List<ItemSummaryResponse> summaries = items.getContent().stream()
                .map(item -> toSummary(item, progressByItem.getOrDefault(item.getId(), ItemProgressResponse.NOT_STARTED)))
                .toList();
        return new ItemPageResponse(summaries, page, size, items.getTotalElements(), items.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ItemResponse getItem(Long itemId) {
        StudyItemEntity item = findOwnedItem(itemId);
        return toResponse(item, topicService.findOwnedTopic(item.getTopicId()));
    }

    @Transactional
    public ItemResponse createItem(ItemRequest request) {
        return createItem(request, new StudyItemEntity(ItemSource.MANUAL));
    }

    /** Creates an item from an approved import candidate: source IMPORT, linked to the job. */
    @Transactional
    public ItemResponse createImportedItem(ItemRequest request, Long importJobId) {
        return createItem(request, StudyItemEntity.fromImport(importJobId));
    }

    /**
     * Ids of existing items in the subject with the given content hashes ({@link ItemContent#computeContentHash}),
     * keyed by hash; used to flag duplicate import candidates.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> findItemIdsByContentHash(Long subjectId, Collection<String> contentHashes) {
        if (contentHashes.isEmpty()) {
            return Map.of();
        }
        return studyItemRepository.findContentHashMatches(subjectId, contentHashes).stream()
                .collect(Collectors.toMap(StudyItemRepository.ContentHashMatch::getContentHash,
                        StudyItemRepository.ContentHashMatch::getItemId));
    }

    /**
     * Items of the topic's subject that have the same question as the one being written (same
     * {@link ItemContent#computeContentHash content hash}), at most {@value #MAX_DUPLICATES}.
     */
    @Transactional(readOnly = true)
    public List<DuplicateItemResponse> findDuplicateItems(DuplicateCheckRequest request) {
        TopicEntity topic = topicService.findOwnedTopic(request.topicId());
        String contentHash = ItemContent.computeContentHash(request.title(),
                htmlSanitizer.sanitizeHtml(request.questionHtml()));
        Long excludedItemId = request.itemId() == null ? -1L : request.itemId();
        return studyItemRepository.findDuplicates(topic.getSubjectId(), contentHash, excludedItemId,
                        PageRequest.of(0, MAX_DUPLICATES)).stream()
                .map(row -> new DuplicateItemResponse(row.getId(), row.getTitle(), row.getTopicId(), row.getTopicName()))
                .toList();
    }

    private ItemResponse createItem(ItemRequest request, StudyItemEntity item) {
        TopicEntity topic = topicService.findOwnedTopic(request.topicId());
        applyRequest(item, request, topic);
        item.setSortOrder(request.sortOrder() != null
                ? request.sortOrder()
                : studyItemRepository.findMaxSortOrder(topic.getId()) + 1);
        StudyItemEntity saved = studyItemRepository.save(item);
        linkMedia(saved);
        return toResponse(saved, topic);
    }

    /** Replaces every field; changing {@code topicId} moves the item to another topic of the current user. */
    @Transactional
    public ItemResponse replaceItem(Long itemId, ItemRequest request) {
        StudyItemEntity item = findOwnedItem(itemId);
        TopicEntity topic = topicService.findOwnedTopic(request.topicId());
        boolean topicChanged = !topic.getId().equals(item.getTopicId());
        applyRequest(item, request, topic);
        if (request.sortOrder() != null) {
            item.setSortOrder(request.sortOrder());
        } else if (topicChanged) {
            item.setSortOrder(studyItemRepository.findMaxSortOrder(topic.getId()) + 1);
        }
        StudyItemEntity saved = studyItemRepository.saveAndFlush(item);
        linkMedia(saved);
        return toResponse(saved, topic);
    }

    /** Deletes the item and the images used in it. */
    @Transactional
    public void deleteItem(Long itemId) {
        StudyItemEntity item = findOwnedItem(itemId);
        mediaService.deleteItemMedia(item.getId());
        studyItemRepository.delete(item);
    }

    private void linkMedia(StudyItemEntity item) {
        Set<Long> mediaIds = new LinkedHashSet<>();
        mediaIds.addAll(htmlSanitizer.findMediaIds(item.getQuestionHtml()));
        mediaIds.addAll(htmlSanitizer.findMediaIds(item.getAnswerHtml()));
        mediaIds.addAll(htmlSanitizer.findMediaIds(item.getNotesHtml()));
        mediaService.linkItemMedia(item.getId(), mediaIds);
    }

    /** Returns the item if its topic belongs to the current user; other users' items are reported as missing. */
    @Transactional(readOnly = true)
    public StudyItemEntity findOwnedItem(Long itemId) {
        StudyItemEntity item = studyItemRepository.findById(itemId)
                .orElseThrow(() -> itemNotFound(itemId));
        try {
            topicService.findOwnedTopic(item.getTopicId());
        } catch (NoSuchElementException exception) {
            throw itemNotFound(itemId);
        }
        return item;
    }

    private NoSuchElementException itemNotFound(Long itemId) {
        return new NoSuchElementException("Item " + itemId + " not found");
    }

    private void applyRequest(StudyItemEntity item, ItemRequest request, TopicEntity topic) {
        String title = request.title().strip();
        String questionHtml = htmlSanitizer.sanitizeHtml(request.questionHtml());
        String answerHtml = htmlSanitizer.sanitizeHtml(request.answerHtml());
        item.placeInTopic(topic.getId(), topic.getSubjectId(), currentUser.getUserId());
        item.setItemType(request.itemType());
        item.setTitle(title);
        item.setQuestionHtml(questionHtml);
        item.setAnswerHtml(answerHtml);
        item.setNotesHtml(htmlSanitizer.sanitizeHtml(request.notesHtml()));
        item.setAnswerPlain(ItemContent.toPlainText(answerHtml));
        item.setDifficulty(request.difficulty() != null ? request.difficulty() : Difficulty.MEDIUM);
        item.setContentHash(ItemContent.computeContentHash(title, questionHtml));
        item.replaceTags(resolveTags(request.tags()));
    }

    /** Finds the user's tags by name and creates the missing ones. */
    private Set<TagEntity> resolveTags(List<String> requestedTags) {
        if (requestedTags == null || requestedTags.isEmpty()) {
            return Set.of();
        }
        long userId = currentUser.getUserId();
        Set<String> names = requestedTags.stream().map(this::normalizeTag)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, TagEntity> existingByName = tagRepository.findByUserIdAndNameIn(userId, names).stream()
                .collect(Collectors.toMap(TagEntity::getName, Function.identity()));
        Set<TagEntity> tags = new HashSet<>(existingByName.values());
        for (String name : names) {
            if (!existingByName.containsKey(name)) {
                tags.add(tagRepository.save(new TagEntity(userId, name)));
            }
        }
        return tags;
    }

    private String normalizeTag(String tag) {
        return tag.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static List<String> sortedTagNames(StudyItemEntity item) {
        return item.getTags().stream().map(TagEntity::getName).sorted().toList();
    }

    /** The current user's status of each item (items without progress are left out, meaning NOT_STARTED). */
    @Transactional(readOnly = true)
    public Map<Long, ItemStatus> findItemStatuses(List<Long> itemIds) {
        Map<Long, ItemStatus> statuses = new HashMap<>();
        findProgress(itemIds).forEach((itemId, progress) -> statuses.put(itemId, progress.status()));
        return statuses;
    }

    private Map<Long, ItemProgressResponse> findProgress(List<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return studyItemRepository.findItemProgress(currentUser.getUserId(), itemIds).stream()
                .collect(Collectors.toMap(StudyItemRepository.ItemProgressRow::getItemId, ItemService::toProgress));
    }

    private static ItemProgressResponse toProgress(StudyItemRepository.ItemProgressRow row) {
        Integer confidence = row.getConfidence() == null ? null : row.getConfidence().intValue();
        return new ItemProgressResponse(row.getStatus(), confidence, row.isBookmarked(), row.getReviewCount(),
                row.getLastReviewedAt(), row.getNextReviewAt());
    }

    private ItemSummaryResponse toSummary(StudyItemEntity item, ItemProgressResponse progress) {
        boolean hasAnswer = item.getAnswerPlain() != null || item.getAnswerHtml() != null;
        return new ItemSummaryResponse(item.getId(), item.getTopicId(), item.getItemType(), item.getTitle(),
                item.getDifficulty(), sortedTagNames(item), item.getSortOrder(), hasAnswer, progress.status(),
                progress.bookmarked(), item.getUpdatedAt());
    }

    private ItemResponse toResponse(StudyItemEntity item, TopicEntity topic) {
        List<Long> itemIds = studyItemRepository.findItemIdsInOrder(topic.getId());
        int position = itemIds.indexOf(item.getId());
        Long previousItemId = position > 0 ? itemIds.get(position - 1) : null;
        Long nextItemId = position >= 0 && position < itemIds.size() - 1 ? itemIds.get(position + 1) : null;
        ItemProgressResponse progress = findProgress(List.of(item.getId()))
                .getOrDefault(item.getId(), ItemProgressResponse.NOT_STARTED);
        return new ItemResponse(item.getId(), topic.getId(), topic.getSubjectId(), item.getItemType(),
                item.getTitle(), item.getQuestionHtml(), item.getAnswerHtml(), item.getNotesHtml(),
                item.getDifficulty(), item.getSource(), sortedTagNames(item), item.getSortOrder(),
                previousItemId, nextItemId, progress, item.getCreatedAt(), item.getUpdatedAt());
    }
}
