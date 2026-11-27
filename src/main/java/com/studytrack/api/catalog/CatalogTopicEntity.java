package com.studytrack.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A node of a catalog subject's topic tree. Relations are plain id columns so the tree is built from one query. */
@Entity
@Table(name = "catalog_topics")
public class CatalogTopicEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "catalog_subject_id", nullable = false, updatable = false)
    private Long catalogSubjectId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CatalogTopicEntity() {
    }

    public CatalogTopicEntity(Long catalogSubjectId, Long parentId, String name, int sortOrder) {
        this.catalogSubjectId = catalogSubjectId;
        this.parentId = parentId;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getCatalogSubjectId() {
        return catalogSubjectId;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
