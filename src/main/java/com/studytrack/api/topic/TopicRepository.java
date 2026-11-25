package com.studytrack.api.topic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<TopicEntity, Long> {

    List<TopicEntity> findBySubjectIdOrderBySortOrderAscIdAsc(Long subjectId);

    @Query("""
            select t from TopicEntity t
            where t.id = :topicId
              and t.subjectId in (select s.id from SubjectEntity s where s.userId = :userId)
            """)
    Optional<TopicEntity> findOwnedTopic(Long topicId, Long userId);

    @Query("""
            select coalesce(max(t.sortOrder), -1) from TopicEntity t
            where t.subjectId = :subjectId
              and ((:parentId is null and t.parentId is null) or t.parentId = :parentId)
            """)
    int findMaxSortOrder(Long subjectId, Long parentId);

    @Query(value = """
            SELECT i.topic_id AS topicId, COUNT(*) AS itemCount
            FROM study_items i JOIN topics t ON t.id = i.topic_id
            WHERE t.subject_id = :subjectId GROUP BY i.topic_id
            """, nativeQuery = true)
    List<TopicItemCount> countItemsByTopic(Long subjectId);

    interface TopicItemCount {
        Long getTopicId();

        long getItemCount();
    }
}
