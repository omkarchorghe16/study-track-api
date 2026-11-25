package com.studytrack.api.item;

import com.studytrack.api.item.ItemDtos.ItemPageResponse;
import com.studytrack.api.item.ItemDtos.ItemRequest;
import com.studytrack.api.item.ItemDtos.ItemResponse;
import com.studytrack.api.item.ItemDtos.ItemSummaryResponse;
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
    private final CurrentUser currentUser;

    public ItemService(StudyItemRepository studyItemRepository, TagRepository tagRepository,
                       TopicService topicService, CurrentUser currentUser) {
        this.studyItemRepository = studyItemRepository;
        this.tagRepository = tagRepository;
        this.topicService = topicService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public ItemPageResponse listTopicItems(Long topicId, Difficulty difficulty, String tag, int page, int size) {
        TopicEntity topic = topicService.findOwnedTopic(topicId);
        String tagName = tag == null || tag.isBlank() ? null : normalizeTag(tag);
        Page<StudyItemEntity> items = studyItemRepository.findTopicItems(topic.getId(), difficulty, tagName,
                PageRequest.of(page, size));
        List<ItemSummaryResponse> summaries = items.getContent().stream().map(this::toSummary).toList();
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
        return toResponse(studyItemRepository.save(item), topic);
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
        return toResponse(studyItemRepository.saveAndFlush(item), topic);
    }

    @Transactional
    public void deleteItem(Long itemId) {
        studyItemRepository.delete(findOwnedItem(itemId));
    }

    private StudyItemEntity findOwnedItem(Long itemId) {
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
        item.setTopicId(topic.getId());
        item.setItemType(request.itemType());
        item.setTitle(title);
        item.setQuestionHtml(blankToNull(request.questionHtml()));
        item.setAnswerHtml(blankToNull(request.answerHtml()));
        item.setNotesHtml(blankToNull(request.notesHtml()));
        item.setAnswerPlain(ItemContent.toPlainText(request.answerHtml()));
        item.setDifficulty(request.difficulty() != null ? request.difficulty() : Difficulty.MEDIUM);
        item.setContentHash(ItemContent.computeContentHash(title, request.questionHtml()));
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

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static List<String> sortedTagNames(StudyItemEntity item) {
        return item.getTags().stream().map(TagEntity::getName).sorted().toList();
    }

    private ItemSummaryResponse toSummary(StudyItemEntity item) {
        return new ItemSummaryResponse(item.getId(), item.getTopicId(), item.getItemType(), item.getTitle(),
                item.getDifficulty(), sortedTagNames(item), item.getSortOrder(), item.getAnswerPlain() != null
                || item.getAnswerHtml() != null, item.getUpdatedAt());
    }

    private ItemResponse toResponse(StudyItemEntity item, TopicEntity topic) {
        List<Long> itemIds = studyItemRepository.findItemIdsInOrder(topic.getId());
        int position = itemIds.indexOf(item.getId());
        Long previousItemId = position > 0 ? itemIds.get(position - 1) : null;
        Long nextItemId = position >= 0 && position < itemIds.size() - 1 ? itemIds.get(position + 1) : null;
        return new ItemResponse(item.getId(), topic.getId(), topic.getSubjectId(), item.getItemType(),
                item.getTitle(), item.getQuestionHtml(), item.getAnswerHtml(), item.getNotesHtml(),
                item.getDifficulty(), item.getSource(), sortedTagNames(item), item.getSortOrder(),
                previousItemId, nextItemId, item.getCreatedAt(), item.getUpdatedAt());
    }
}
