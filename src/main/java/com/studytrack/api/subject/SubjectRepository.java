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

    List<SubjectEntity> findByUserIdAndCatalogSubjectIdIn(Long userId, Collection<Long> catalogSubjectIds);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);

    @Query("select coalesce(max(s.sortOrder), -1) from SubjectEntity s where s.userId = :userId")
    int findMaxSortOrder(Long userId);

    /** Topic and item totals per subject, read with grouped queries instead of one per subject. */
    @Query(value = """
            SELECT t.subject_id AS subjectId, COUNT(*) AS topicCount
            FROM study_track.topics t WHERE t.subject_id IN (:subjectIds) GROUP BY t.subject_id
            """, nativeQuery = true)
    List<SubjectCount> countTopicsBySubject(Collection<Long> subjectIds);

    /** Item totals per subject with the user's DONE and LEARNING counts (no progress row = NOT_STARTED). */
    @Query(value = """
            SELECT t.subject_id AS subjectId, COUNT(*) AS itemCount,
                   SUM(CASE WHEN p.status = 'DONE' THEN 1 ELSE 0 END) AS doneCount,
                   SUM(CASE WHEN p.status = 'LEARNING' THEN 1 ELSE 0 END) AS learningCount
            FROM study_track.questions i JOIN study_track.topics t ON t.id = i.topic_id
            LEFT JOIN study_track.question_progress p ON p.question_id = i.id AND p.user_id = :userId
            WHERE t.subject_id IN (:subjectIds) GROUP BY t.subject_id
            """, nativeQuery = true)
    List<SubjectItemCount> countItemsBySubject(Collection<Long> subjectIds, Long userId);

    /** Topics the user marked complete, per subject. */
    @Query(value = """
            SELECT t.subject_id AS subjectId, COUNT(*) AS topicCount
            FROM study_track.topic_progress tp JOIN study_track.topics t ON t.id = tp.topic_id
            WHERE t.subject_id IN (:subjectIds) AND tp.user_id = :userId AND tp.completed = TRUE
            GROUP BY t.subject_id
            """, nativeQuery = true)
    List<SubjectCount> countCompletedTopicsBySubject(Collection<Long> subjectIds, Long userId);

    interface SubjectCount {
        Long getSubjectId();

        long getTopicCount();
    }

    interface SubjectItemCount {
        Long getSubjectId();

        long getItemCount();

        long getDoneCount();

        long getLearningCount();
    }
}
