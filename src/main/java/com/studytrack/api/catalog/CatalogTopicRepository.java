package com.studytrack.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogTopicRepository extends JpaRepository<CatalogTopicEntity, Long> {

    List<CatalogTopicEntity> findByCatalogSubjectIdOrderBySortOrderAscIdAsc(Long catalogSubjectId);
}
