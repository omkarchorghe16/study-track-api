package com.studytrack.api.subject;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<SubjectEntity, Long> {

    List<SubjectEntity> findByUserIdOrderBySortOrderAscNameAsc(Long userId);

    Optional<SubjectEntity> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);

    @Query("select coalesce(max(s.sortOrder), -1) from SubjectEntity s where s.userId = :userId")
    int findMaxSortOrder(Long userId);

    /** Topic and item totals per subject, read with two grouped queries instead of one per subject. */
    @Query(value = """
            SELECT t.subject_id AS subjectId, COUNT(*) AS topicCount
            FROM topics t WHERE t.subject_id IN (:subjectIds) GROUP BY t.subject_id
            """, nativeQuery = true)
    List<SubjectCount> countTopicsBySubject(Collection<Long> subjectIds);

    @Query(value = """
            SELECT t.subject_id AS subjectId, COUNT(*) AS itemCount
            FROM study_items i JOIN topics t ON t.id = i.topic_id
            WHERE t.subject_id IN (:subjectIds) GROUP BY t.subject_id
            """, nativeQuery = true)
    List<SubjectItemCount> countItemsBySubject(Collection<Long> subjectIds);

    interface SubjectCount {
        Long getSubjectId();

        long getTopicCount();
    }

    interface SubjectItemCount {
        Long getSubjectId();

        long getItemCount();
    }
}
