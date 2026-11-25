package com.studytrack.api.progress;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TopicProgressRepository extends JpaRepository<TopicProgressEntity, Long> {

    Optional<TopicProgressEntity> findByUserIdAndTopicId(Long userId, Long topicId);
}
