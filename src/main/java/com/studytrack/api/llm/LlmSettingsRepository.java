package com.studytrack.api.llm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LlmSettingsRepository extends JpaRepository<LlmSettingsEntity, Long> {

    Optional<LlmSettingsEntity> findByUserId(Long userId);
}
