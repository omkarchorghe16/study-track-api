package com.studytrack.api.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
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

    /**
     * Images created before {@code createdBefore} that nothing uses: not linked to an item, not an import's
     * document, and not referenced as {@code src="/api/v1/media/{id}"} by any item, topic description or
     * open (PENDING or EDITED) import candidate. The HTML check catches images shared by several items,
     * since an image is linked to only one of them.
     */
    @Query(nativeQuery = true, value = """
            select m.* from media_files m
            where m.item_id is null and m.created_at < :createdBefore
              and not exists (select 1 from import_jobs j where j.media_id = m.id)
              and not exists (select 1 from study_items i
                              where i.question_html like concat('%/api/v1/media/', m.id, '"%')
                                 or i.answer_html like concat('%/api/v1/media/', m.id, '"%')
                                 or i.notes_html like concat('%/api/v1/media/', m.id, '"%'))
              and not exists (select 1 from topics t where t.description_html like concat('%/api/v1/media/', m.id, '"%'))
              and not exists (select 1 from import_candidates c
                              where c.status in ('PENDING', 'EDITED')
                                and (c.question_html like concat('%/api/v1/media/', m.id, '"%')
                                     or c.answer_html like concat('%/api/v1/media/', m.id, '"%')
                                     or c.notes_html like concat('%/api/v1/media/', m.id, '"%')))
            order by m.id
            """)
    List<MediaFileEntity> findUnusedMedia(Instant createdBefore);
}
