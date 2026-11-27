package com.studytrack.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface CatalogSubjectRepository extends JpaRepository<CatalogSubjectEntity, Long> {

    List<CatalogSubjectEntity> findByTrackIdOrderBySortOrderAscNameAsc(Long trackId);

    boolean existsByTrackId(Long trackId);

    /** Number of topics (all levels) per catalog subject, read with one grouped query. */
    @Query(value = """
            SELECT t.catalog_subject_id AS catalogSubjectId, COUNT(*) AS topicCount
            FROM study_track.catalog_topics t WHERE t.catalog_subject_id IN (:catalogSubjectIds)
            GROUP BY t.catalog_subject_id
            """, nativeQuery = true)
    List<SubjectTopicCount> countTopicsBySubject(Collection<Long> catalogSubjectIds);

    /** Number of starter questions (all topics) per catalog subject, read with one grouped query. */
    @Query(value = """
            SELECT t.catalog_subject_id AS catalogSubjectId, COUNT(*) AS questionCount
            FROM study_track.catalog_questions q JOIN study_track.catalog_topics t ON t.id = q.catalog_topic_id
            WHERE t.catalog_subject_id IN (:catalogSubjectIds) GROUP BY t.catalog_subject_id
            """, nativeQuery = true)
    List<SubjectQuestionCount> countQuestionsBySubject(Collection<Long> catalogSubjectIds);

    interface SubjectTopicCount {
        Long getCatalogSubjectId();

        long getTopicCount();
    }

    interface SubjectQuestionCount {
        Long getCatalogSubjectId();

        long getQuestionCount();
    }
}
