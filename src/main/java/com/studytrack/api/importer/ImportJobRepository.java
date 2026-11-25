package com.studytrack.api.importer;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImportJobRepository extends JpaRepository<ImportJobEntity, Long> {

    Optional<ImportJobEntity> findByIdAndUserId(Long id, Long userId);

    List<ImportJobEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    /** Jobs a restart interrupted: they were running when the server stopped and will never finish. */
    @Modifying
    @Query("""
            update ImportJobEntity j set j.status = com.studytrack.api.importer.ImportJobStatus.FAILED,
                   j.errorMessage = :message, j.updatedAt = :now
            where j.status in :statuses
            """)
    int failJobsInStatus(Collection<ImportJobStatus> statuses, String message, Instant now);
}
