package com.studytrack.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface CatalogTrackRepository extends JpaRepository<CatalogTrackEntity, Long> {

    List<CatalogTrackEntity> findAllByOrderBySortOrderAscNameAsc();

    /** Number of subjects per track, read with one grouped query. */
    @Query(value = """
            SELECT s.track_id AS trackId, COUNT(*) AS subjectCount
            FROM study_track.catalog_subjects s WHERE s.track_id IN (:trackIds) GROUP BY s.track_id
            """, nativeQuery = true)
    List<TrackSubjectCount> countSubjectsByTrack(Collection<Long> trackIds);

    interface TrackSubjectCount {
        Long getTrackId();

        long getSubjectCount();
    }
}
