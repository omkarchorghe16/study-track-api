package com.studytrack.api.explanation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SharedExplanationRepository extends JpaRepository<SharedExplanationEntity, Long> {

    Optional<SharedExplanationEntity> findByExplanationKey(String explanationKey);

    boolean existsByExplanationKey(String explanationKey);

    void deleteByExplanationKey(String explanationKey);
}
