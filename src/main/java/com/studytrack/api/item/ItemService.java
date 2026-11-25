package com.studytrack.api.item;

import com.studytrack.api.content.HtmlSanitizer;
import com.studytrack.api.item.ItemDtos.ItemPageResponse;
import com.studytrack.api.item.ItemDtos.ItemProgressResponse;
import com.studytrack.api.item.ItemDtos.ItemRequest;
import com.studytrack.api.item.ItemDtos.ItemResponse;
import com.studytrack.api.item.ItemDtos.ItemSummaryResponse;
import com.studytrack.api.media.MediaService;
import com.studytrack.api.topic.TopicEntity;
import com.studytrack.api.topic.TopicService;
import com.studytrack.api.user.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final StudyItemRepository studyItemRepository;
    private final TagRepository tagRepository;
    private final TopicService topicService;
    private final MediaService mediaService;
    private final HtmlSanitizer htmlSanitizer;
    private final CurrentUser currentUser;

    public ItemService(StudyItemRepository studyItemRepository, TagRepository tagRepository,
                       TopicService topicService, MediaService mediaService, HtmlSanitizer htmlSanitizer,
                       CurrentUser currentUser) {
        this.studyItemRepository = studyItemRepository;
        this.tagRepository = tagRepository;
        this.topicService = topicService;
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
        TopicEntity topic = topicService.findOwnedTopic(request.topicId());
        StudyItemEntity item = new StudyItemEntity(ItemSource.MANUAL);
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
        item.setTopicId(topic.getId());
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
