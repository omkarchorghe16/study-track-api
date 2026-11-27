package com.studytrack.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CatalogQuestionRepository extends JpaRepository<CatalogQuestionEntity, Long> {

    List<CatalogQuestionEntity> findByCatalogTopicIdOrderBySortOrderAscIdAsc(Long catalogTopicId);

    List<CatalogQuestionEntity> findByCatalogTopicIdInOrderBySortOrderAscIdAsc(Collection<Long> catalogTopicIds);
}
