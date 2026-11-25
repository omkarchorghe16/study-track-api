package com.studytrack.api.progress;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudySessionRepository extends JpaRepository<StudySessionEntity, Long> {

    Optional<StudySessionEntity> findByUserIdAndSubjectId(Long userId, Long subjectId);

    Optional<StudySessionEntity> findFirstByUserIdOrderByUpdatedAtDescIdDesc(Long userId);
}
