package com.studytrack.api.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface StudyItemRepository extends JpaRepository<StudyItemEntity, Long> {

    /**
     * Items of one topic; a null difficulty, tag or status means "any". The status is the user's
     * (no progress row counts as NOT_STARTED, which the caller signals with {@code notStarted}).
     * Ordered by sort order, then id.
     */
    @Query(value = """
            select i from StudyItemEntity i
            where i.topicId = :topicId
              and (:difficulty is null or i.difficulty = :difficulty)
              and (:tag is null or exists (select 1 from i.tags t where t.name = :tag))
              and (:status is null
                   or exists (select 1 from ItemProgressEntity p
                              where p.itemId = i.id and p.userId = :userId and p.status = :status)
                   or (:notStarted = true and not exists (select 1 from ItemProgressEntity p
                              where p.itemId = i.id and p.userId = :userId)))
            order by i.sortOrder, i.id
            """, countQuery = """
            select count(i) from StudyItemEntity i
            where i.topicId = :topicId
              and (:difficulty is null or i.difficulty = :difficulty)
              and (:tag is null or exists (select 1 from i.tags t where t.name = :tag))
              and (:status is null
                   or exists (select 1 from ItemProgressEntity p
                              where p.itemId = i.id and p.userId = :userId and p.status = :status)
                   or (:notStarted = true and not exists (select 1 from ItemProgressEntity p
                              where p.itemId = i.id and p.userId = :userId)))
            """)
    Page<StudyItemEntity> findTopicItems(Long topicId, Difficulty difficulty, String tag, ItemStatus status,
                                         boolean notStarted, Long userId, Pageable pageable);

    /** The user's progress rows for these items (items without a row are NOT_STARTED). */
    @Query("""
            select p.itemId as itemId, p.status as status, p.confidence as confidence, p.bookmarked as bookmarked,
                   p.reviewCount as reviewCount, p.lastReviewedAt as lastReviewedAt, p.nextReviewAt as nextReviewAt
            from ItemProgressEntity p
            where p.userId = :userId and p.itemId in :itemIds
            """)
    List<ItemProgressRow> findItemProgress(Long userId, Collection<Long> itemIds);

    interface ItemProgressRow {
        Long getItemId();

        ItemStatus getStatus();

        Short getConfidence();

        boolean isBookmarked();

        int getReviewCount();

        Instant getLastReviewedAt();

        Instant getNextReviewAt();
    }

    @Query("select i.id from StudyItemEntity i where i.topicId = :topicId order by i.sortOrder, i.id")
    List<Long> findItemIdsInOrder(Long topicId);

    @Query("select coalesce(max(i.sortOrder), -1) from StudyItemEntity i where i.topicId = :topicId")
    int findMaxSortOrder(Long topicId);
}
