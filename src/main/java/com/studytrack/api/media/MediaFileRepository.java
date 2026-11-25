package com.studytrack.api.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaFileRepository extends JpaRepository<MediaFileEntity, Long> {

    Optional<MediaFileEntity> findByIdAndUserId(Long id, Long userId);

    List<MediaFileEntity> findByItemId(Long itemId);

    /** Links the user's unlinked (or already linked to this item) images to the item. */
    @Modifying
    @Query("""
            update MediaFileEntity m set m.itemId = :itemId
            where m.userId = :userId and m.id in :mediaIds and (m.itemId is null or m.itemId = :itemId)
            """)
    int linkToItem(Long itemId, Long userId, Collection<Long> mediaIds);

    /** Unlinks the item's images that its HTML no longer references. */
    @Modifying
    @Query("update MediaFileEntity m set m.itemId = null where m.itemId = :itemId and m.id not in :keptMediaIds")
    int unlinkFromItem(Long itemId, Collection<Long> keptMediaIds);
}
