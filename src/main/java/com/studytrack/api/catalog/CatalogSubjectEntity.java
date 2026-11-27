package com.studytrack.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A shared subject of a track, for example Core Java. */
@Entity
@Table(name = "catalog_subjects")
public class CatalogSubjectEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "track_id", nullable = false, updatable = false)
    private Long trackId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 16)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CatalogSubjectEntity() {
    }

    public CatalogSubjectEntity(Long trackId, String name, int sortOrder) {
        this.trackId = trackId;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getTrackId() {
        return trackId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
