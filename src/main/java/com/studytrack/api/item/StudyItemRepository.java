package com.studytrack.api.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StudyItemRepository extends JpaRepository<StudyItemEntity, Long> {

    /** Items of one topic; a null difficulty or tag means "any". Ordered by sort order, then id. */
    @Query(value = """
            select i from StudyItemEntity i
            where i.topicId = :topicId
              and (:difficulty is null or i.difficulty = :difficulty)
              and (:tag is null or exists (select 1 from i.tags t where t.name = :tag))
            order by i.sortOrder, i.id
            """, countQuery = """
            select count(i) from StudyItemEntity i
            where i.topicId = :topicId
              and (:difficulty is null or i.difficulty = :difficulty)
              and (:tag is null or exists (select 1 from i.tags t where t.name = :tag))
            """)
    Page<StudyItemEntity> findTopicItems(Long topicId, Difficulty difficulty, String tag, Pageable pageable);

    @Query("select i.id from StudyItemEntity i where i.topicId = :topicId order by i.sortOrder, i.id")
    List<Long> findItemIdsInOrder(Long topicId);

    @Query("select coalesce(max(i.sortOrder), -1) from StudyItemEntity i where i.topicId = :topicId")
    int findMaxSortOrder(Long topicId);
}
