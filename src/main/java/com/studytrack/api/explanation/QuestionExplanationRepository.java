package com.studytrack.api.explanation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface QuestionExplanationRepository extends JpaRepository<QuestionExplanationEntity, Long> {

    Optional<QuestionExplanationEntity> findByQuestionIdAndUserId(Long questionId, Long userId);

    /** The user's QUESTION items of a subject that have no explanation yet, in subject order. */
    @Query(value = """
            SELECT q.id FROM study_track.questions q JOIN study_track.topics t ON t.id = q.topic_id
            WHERE q.subject_id = :subjectId AND q.user_id = :userId AND q.question_type = 'QUESTION'
              AND NOT EXISTS (SELECT 1 FROM study_track.question_explanations e WHERE e.question_id = q.id)
            ORDER BY t.sort_order, t.id, q.sort_order, q.id
            """, nativeQuery = true)
    List<Long> findQuestionIdsWithoutExplanation(Long subjectId, Long userId);
}
