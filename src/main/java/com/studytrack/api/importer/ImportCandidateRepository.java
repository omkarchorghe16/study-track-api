package com.studytrack.api.importer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ImportCandidateRepository extends JpaRepository<ImportCandidateEntity, Long> {

    List<ImportCandidateEntity> findByJobIdOrderBySortOrderAscIdAsc(Long jobId);

    List<ImportCandidateEntity> findByJobIdAndIdIn(Long jobId, Collection<Long> ids);

    @Query("select coalesce(max(c.sortOrder), -1) from ImportCandidateEntity c where c.jobId = :jobId")
    int findMaxSortOrder(Long jobId);

    @Query("""
            select c.jobId as jobId, c.status as status, count(c) as candidateCount
            from ImportCandidateEntity c where c.jobId in :jobIds group by c.jobId, c.status
            """)
    List<CandidateStatusCount> countByJobAndStatus(Collection<Long> jobIds);

    interface CandidateStatusCount {
        Long getJobId();

        CandidateStatus getStatus();

        long getCandidateCount();
    }
}
