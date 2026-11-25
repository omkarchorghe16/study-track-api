package com.studytrack.api.item;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TagRepository extends JpaRepository<TagEntity, Long> {

    List<TagEntity> findByUserIdAndNameIn(Long userId, Collection<String> names);
}
