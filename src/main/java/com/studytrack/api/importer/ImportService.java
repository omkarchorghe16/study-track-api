package com.studytrack.api.importer;

import com.studytrack.api.importer.CandidateContentBuilder.CandidateContent;
import com.studytrack.api.importer.ImportDtos.ApproveResponse;
import com.studytrack.api.importer.ImportDtos.CandidateCounts;
import com.studytrack.api.importer.ImportDtos.CandidateRequest;
import com.studytrack.api.importer.ImportDtos.CandidateResponse;
import com.studytrack.api.importer.ImportDtos.CandidateSelectionRequest;
import com.studytrack.api.importer.ImportDtos.ImportJobResponse;
import com.studytrack.api.importer.ImportDtos.RejectResponse;
import com.studytrack.api.item.ItemDtos.ItemRequest;
import com.studytrack.api.item.ItemService;
import com.studytrack.api.item.ItemStatus;
import com.studytrack.api.llm.LlmException;
import com.studytrack.api.llm.LlmSettingsService;
import com.studytrack.api.llm.LlmSettingsService.ImportLlmSettings;
import com.studytrack.api.media.MediaService;
import com.studytrack.api.subject.SubjectEntity;
import com.studytrack.api.subject.SubjectService;
import com.studytrack.api.topic.TopicEntity;
import com.studytrack.api.topic.TopicService;
import com.studytrack.api.topic.TopicService.TopicPathResult;
import com.studytrack.api.user.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Document imports: upload (the work then runs in {@link ImportProcessingService}), progress, and the
 * review of candidates. Nothing reaches {@code study_track.questions} until the user approves it.
 */
@Service
public class ImportService {
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final String INTERRUPTED_MESSAGE = "The server restarted while this import was running; upload the file again";

    private final ImportJobRepository importJobRepository;
    private final ImportCandidateRepository importCandidateRepository;
    private final ImportProcessingService importProcessingService;
    private final CandidateContentBuilder candidateContentBuilder;
    private final ImportProperties importProperties;
    private final SubjectService subjectService;
    private final TopicService topicService;
    private final ItemService itemService;
    private final MediaService mediaService;
    private final LlmSettingsService llmSettingsService;
    private final CurrentUser currentUser;

    public ImportService(ImportJobRepository importJobRepository, ImportCandidateRepository importCandidateRepository,
                         ImportProcessingService importProcessingService,
                         CandidateContentBuilder candidateContentBuilder, ImportProperties importProperties,
                         SubjectService subjectService, TopicService topicService, ItemService itemService,
                         MediaService mediaService, LlmSettingsService llmSettingsService, CurrentUser currentUser) {
        this.importJobRepository = importJobRepository;
        this.importCandidateRepository = importCandidateRepository;
        this.importProcessingService = importProcessingService;
        this.candidateContentBuilder = candidateContentBuilder;
        this.importProperties = importProperties;
        this.subjectService = subjectService;
        this.topicService = topicService;
        this.itemService = itemService;
        this.mediaService = mediaService;
        this.llmSettingsService = llmSettingsService;
        this.currentUser = currentUser;
    }

    /**
     * Stores the file, creates the job (UPLOADED) and starts processing once the row is committed.
     * Fails fast with 409 when imports are switched off or the LLM is not configured (for example a
     * missing API key), 413 when the file is too large and 415 for other formats. CSV files need no LLM:
     * they are stored without a provider or model and work even when LLM imports are off.
     */
    @Transactional
    public ImportJobResponse createImport(MultipartFile file, Long subjectId) {
        SubjectEntity subject = subjectService.findOwnedSubject(subjectId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty");
        }
        if (file.getSize() > importProperties.maxFileBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Documents can be at most " + importProperties.maxFileBytes() / (1024 * 1024) + " MB");
        }
        byte[] content = readBytes(file);
        String fileName = cleanFileName(file.getOriginalFilename());
        ImportFileType fileType = ImportFileType.detectFileType(fileName, content)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Only .docx, .pdf, .md, .txt and .csv files can be imported"));
        ImportLlmSettings llmSettings = fileType.usesLlm() ? findUsableLlmSettings() : null;

        Long mediaId = mediaService.storeDocument(content, fileName, fileType.contentType(),
                fileType.name().toLowerCase(Locale.ROOT));
        ImportJobEntity job = new ImportJobEntity(currentUser.getUserId(), subject.getId());
        job.setFileName(fileName);
        job.setFileType(fileType);
        job.setMediaId(mediaId);
        if (llmSettings != null) {
            job.setLlmProvider(llmSettings.connection().provider());
            job.setLlmModel(llmSettings.connection().model());
        }
        ImportJobEntity saved = importJobRepository.save(job);
        startAfterCommit(saved.getId());
        return toJobResponse(saved, CandidateCounts.NONE);
    }

    /** The user's LLM settings, or 409 when imports are off or the client cannot be built (missing key). */
    private ImportLlmSettings findUsableLlmSettings() {
        ImportLlmSettings llmSettings = llmSettingsService.findImportSettings(currentUser.getUserId());
        if (!llmSettings.enabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Imports are switched off in Settings");
        }
        try {
            llmSettingsService.createClient(llmSettings.connection());
        } catch (LlmException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        return llmSettings;
    }

    @Transactional(readOnly = true)
    public List<ImportJobResponse> listImports(int limit) {
        List<ImportJobEntity> jobs = importJobRepository.findByUserIdOrderByCreatedAtDescIdDesc(
                currentUser.getUserId(), PageRequest.of(0, limit));
        Map<Long, CandidateCounts> counts = countCandidates(jobs.stream().map(ImportJobEntity::getId).toList());
        return jobs.stream().map(job -> toJobResponse(job, counts.getOrDefault(job.getId(), CandidateCounts.NONE)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ImportJobResponse getImport(Long jobId) {
        return toJobResponse(findOwnedJob(jobId));
    }

    /**
     * All drafts in document order. Saved ones carry the current user's status of their question
     * ({@code questionStatus}, DONE when ticked as completed); open and DUPLICATE ones carry what they duplicate.
     */
    @Transactional(readOnly = true)
    public List<CandidateResponse> listCandidates(Long jobId) {
        ImportJobEntity job = findOwnedJob(jobId);
        List<ImportCandidateEntity> candidates = importCandidateRepository.findByJobIdOrderBySortOrderAscIdAsc(job.getId());
        Set<String> duplicateHashes = candidates.stream()
                .filter(candidate -> isDuplicateCandidate(candidate) && candidate.getContentHash() != null)
                .map(ImportCandidateEntity::getContentHash)
                .collect(Collectors.toSet());
        Map<String, Long> existingItems = itemService.findItemIdsByContentHash(job.getSubjectId(), duplicateHashes);
        Map<Long, ItemStatus> questionStatuses = itemService.findItemStatuses(candidates.stream()
                .map(ImportCandidateEntity::getCreatedItemId).filter(id -> id != null).toList());
        Map<String, Long> firstCandidateByHash = new HashMap<>();
        List<CandidateResponse> responses = new ArrayList<>();
        for (ImportCandidateEntity candidate : candidates) {
            Long duplicateItemId = null;
            Long duplicateOfCandidateId = null;
            String hash = candidate.getContentHash();
            Long firstWithHash = hash == null ? null : firstCandidateByHash.putIfAbsent(hash, candidate.getId());
            if (isDuplicateCandidate(candidate) && hash != null) {
                duplicateItemId = existingItems.get(hash);
                duplicateOfCandidateId = duplicateItemId == null ? firstWithHash : null;
            }
            ItemStatus questionStatus = candidate.getCreatedItemId() == null ? null
                    : questionStatuses.getOrDefault(candidate.getCreatedItemId(), ItemStatus.NOT_STARTED);
            responses.add(toCandidateResponse(candidate, duplicateItemId, duplicateOfCandidateId, questionStatus));
        }
        return responses;
    }

    private static boolean isDuplicateCandidate(ImportCandidateEntity candidate) {
        return candidate.getStatus().isOpen() || candidate.getStatus() == CandidateStatus.DUPLICATE;
    }

    @Transactional
    public CandidateResponse updateCandidate(Long jobId, Long candidateId, CandidateRequest request) {
        ImportJobEntity job = findReviewableJob(jobId);
        ImportCandidateEntity candidate = importCandidateRepository.findByJobIdAndIdIn(job.getId(), List.of(candidateId))
                .stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("Candidate " + candidateId + " not found"));
        if (!candidate.getStatus().isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Candidate " + candidateId + " was already " + candidate.getStatus().name().toLowerCase());
        }
        if (request.targetTopicId() != null) {
            requireTopicInSubject(request.targetTopicId(), job.getSubjectId());
        }
        candidateContentBuilder.applyContent(candidate, new CandidateContent(request.itemType(), request.title(),
                request.questionHtml(), request.answerHtml(), request.notesHtml(), request.difficulty(),
                request.tags(), request.topicPath()));
        candidate.setTargetTopicId(request.targetTopicId());
        candidate.setStatus(CandidateStatus.EDITED);
        return toCandidateResponse(importCandidateRepository.saveAndFlush(candidate), null, null, null);
    }

    /**
     * Creates a question for each selected open draft, in document order: in its target topic, or in the topic
     * its path names (created if missing; drafts without a path go to a topic named after the file). A draft
     * whose question the subject already has, or that repeats an earlier one, is marked DUPLICATE instead.
     * Images in the HTML are linked to the new questions. The job is COMPLETED once no draft is open.
     */
    @Transactional
    public ApproveResponse approveCandidates(Long jobId, CandidateSelectionRequest request) {
        ImportJobEntity job = findReviewableJob(jobId);
        List<ImportCandidateEntity> selected = selectOpenCandidates(job, request);
        Set<String> hashes = selected.stream()
                .map(ImportCandidateEntity::getContentHash)
                .filter(hash -> hash != null)
                .collect(Collectors.toSet());
        Set<String> savedHashes = new HashSet<>(itemService.findItemIdsByContentHash(job.getSubjectId(), hashes).keySet());
        Map<String, Long> topicIdByPath = new HashMap<>();
        int createdTopicCount = 0;
        List<Long> createdItemIds = new ArrayList<>();
        for (ImportCandidateEntity candidate : selected) {
            String hash = candidate.getContentHash();
            if (hash != null && !savedHashes.add(hash)) {
                candidate.setStatus(CandidateStatus.DUPLICATE);
                continue;
            }
            Long topicId = candidate.getTargetTopicId();
            if (topicId == null) {
                List<String> path = CandidateContentBuilder.splitTopicPath(candidate.getSuggestedTopicPath());
                if (path.isEmpty()) {
                    path = List.of(fallbackTopicName(job.getFileName()));
                }
                String pathKey = String.join("\u0000", path).toLowerCase(Locale.ROOT);
                topicId = topicIdByPath.get(pathKey);
                if (topicId == null) {
                    TopicPathResult result = topicService.findOrCreateTopicPath(job.getSubjectId(), path);
                    topicId = result.topic().getId();
                    createdTopicCount += result.createdTopicCount();
                    topicIdByPath.put(pathKey, topicId);
                }
            }
            Long itemId = itemService.createImportedItem(new ItemRequest(topicId, candidate.getItemType(),
                    candidate.getTitle(), candidate.getQuestionHtml(), candidate.getAnswerHtml(),
                    candidate.getNotesHtml(), candidate.getDifficulty(), candidate.getTagList(), null), job.getId()).id();
            candidate.setStatus(CandidateStatus.APPROVED);
            candidate.setCreatedItemId(itemId);
            createdItemIds.add(itemId);
        }
        importCandidateRepository.saveAll(selected);
        ImportJobEntity updated = completeIfReviewed(job);
        return new ApproveResponse(createdItemIds.size(), createdTopicCount, createdItemIds, toJobResponse(updated));
    }

    /**
     * Saves an import's drafts as questions as soon as the import has found them, with no review step (see
     * {@link #approveCandidates}: duplicates are marked DUPLICATE, not saved). Runs on the import thread.
     */
    @EventListener
    @Transactional
    public void saveImportDrafts(ImportDraftsReadyEvent event) {
        approveCandidates(event.jobId(), new CandidateSelectionRequest(null, true));
    }

    @Transactional
    public RejectResponse rejectCandidates(Long jobId, CandidateSelectionRequest request) {
        ImportJobEntity job = findReviewableJob(jobId);
        List<ImportCandidateEntity> selected = selectOpenCandidates(job, request);
        selected.forEach(candidate -> candidate.setStatus(CandidateStatus.REJECTED));
        importCandidateRepository.saveAll(selected);
        ImportJobEntity updated = completeIfReviewed(job);
        return new RejectResponse(selected.size(), toJobResponse(updated));
    }

    /** A restart stops running imports for good; mark them FAILED so the UI does not poll forever. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedImports() {
        int failed = importJobRepository.failJobsInStatus(
                List.of(ImportJobStatus.UPLOADED, ImportJobStatus.PARSING, ImportJobStatus.EXTRACTING),
                INTERRUPTED_MESSAGE, Instant.now());
        if (failed > 0) {
            log.warn("Marked imports interrupted by a restart as failed: count={}", failed);
        }
    }

    private void startAfterCommit(Long jobId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            importProcessingService.processImport(jobId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                importProcessingService.processImport(jobId);
            }
        });
    }

    private ImportJobEntity findOwnedJob(Long jobId) {
        return importJobRepository.findByIdAndUserId(jobId, currentUser.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Import " + jobId + " not found"));
    }

    /** Candidates can be reviewed once extraction has stopped, also after a failure part-way through. */
    private ImportJobEntity findReviewableJob(Long jobId) {
        ImportJobEntity job = findOwnedJob(jobId);
        if (job.getStatus().isRunning()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The import is still running");
        }
        return job;
    }

    private List<ImportCandidateEntity> selectOpenCandidates(ImportJobEntity job, CandidateSelectionRequest request) {
        List<ImportCandidateEntity> candidates;
        if (request.all()) {
            candidates = importCandidateRepository.findByJobIdOrderBySortOrderAscIdAsc(job.getId());
        } else {
            if (request.candidateIds() == null || request.candidateIds().isEmpty()) {
                throw new IllegalArgumentException("Give candidateIds, or set all to true");
            }
            candidates = importCandidateRepository.findByJobIdAndIdIn(job.getId(), request.candidateIds());
            if (candidates.size() != Set.copyOf(request.candidateIds()).size()) {
                throw new NoSuchElementException("Some candidates do not belong to import " + job.getId());
            }
            candidates = candidates.stream()
                    .sorted((left, right) -> left.getSortOrder() != right.getSortOrder()
                            ? Integer.compare(left.getSortOrder(), right.getSortOrder())
                            : Long.compare(left.getId(), right.getId()))
                    .toList();
        }
        return candidates.stream().filter(candidate -> candidate.getStatus().isOpen()).toList();
    }

    private ImportJobEntity completeIfReviewed(ImportJobEntity job) {
        boolean anyOpen = importCandidateRepository.findByJobIdOrderBySortOrderAscIdAsc(job.getId()).stream()
                .anyMatch(candidate -> candidate.getStatus().isOpen());
        if (!anyOpen && job.getStatus() == ImportJobStatus.READY_FOR_REVIEW) {
            job.setStatus(ImportJobStatus.COMPLETED);
            return importJobRepository.saveAndFlush(job);
        }
        return job;
    }

    private void requireTopicInSubject(Long topicId, Long subjectId) {
        TopicEntity topic = topicService.findOwnedTopic(topicId);
        if (!topic.getSubjectId().equals(subjectId)) {
            throw new IllegalArgumentException("Topic " + topicId + " belongs to another subject");
        }
    }

    private Map<Long, CandidateCounts> countCandidates(List<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<CandidateStatus, Long>> byJob = new HashMap<>();
        for (ImportCandidateRepository.CandidateStatusCount row : importCandidateRepository.countByJobAndStatus(jobIds)) {
            byJob.computeIfAbsent(row.getJobId(), id -> new EnumMap<>(CandidateStatus.class))
                    .put(row.getStatus(), row.getCandidateCount());
        }
        Map<Long, CandidateCounts> counts = new HashMap<>();
        byJob.forEach((jobId, statusCounts) -> {
            long pending = statusCounts.getOrDefault(CandidateStatus.PENDING, 0L);
            long edited = statusCounts.getOrDefault(CandidateStatus.EDITED, 0L);
            long approved = statusCounts.getOrDefault(CandidateStatus.APPROVED, 0L);
            long rejected = statusCounts.getOrDefault(CandidateStatus.REJECTED, 0L);
            long duplicates = statusCounts.getOrDefault(CandidateStatus.DUPLICATE, 0L);
            counts.put(jobId, new CandidateCounts(pending + edited + approved + rejected + duplicates, pending + edited,
                    approved, rejected, duplicates));
        });
        return counts;
    }

    private ImportJobResponse toJobResponse(ImportJobEntity job) {
        return toJobResponse(job, countCandidates(List.of(job.getId())).getOrDefault(job.getId(), CandidateCounts.NONE));
    }

    private static ImportJobResponse toJobResponse(ImportJobEntity job, CandidateCounts counts) {
        int percent = job.getTotalChunks() == 0 ? 0 : job.getProcessedChunks() * 100 / job.getTotalChunks();
        if (!job.getStatus().isRunning() && job.getStatus() != ImportJobStatus.FAILED) {
            percent = 100;
        }
        return new ImportJobResponse(job.getId(), job.getSubjectId(), job.getFileName(), job.getFileType(),
                job.getStatus(), job.getLlmProvider(), job.getLlmModel(), job.getTotalChunks(),
                job.getProcessedChunks(), job.getFailedChunks(), percent, job.getInputTokens(), job.getOutputTokens(),
                job.getErrorMessage(), counts, job.getCreatedAt(), job.getUpdatedAt());
    }

    private static CandidateResponse toCandidateResponse(ImportCandidateEntity candidate, Long duplicateItemId,
                                                         Long duplicateOfCandidateId, ItemStatus questionStatus) {
        Double confidence = candidate.getConfidenceScore() == null ? null : candidate.getConfidenceScore().doubleValue();
        return new CandidateResponse(candidate.getId(), candidate.getChunkIndex(), candidate.getStatus(),
                candidate.getItemType(), candidate.getTitle(), candidate.getQuestionHtml(), candidate.getAnswerHtml(),
                candidate.getNotesHtml(), candidate.getDifficulty(), candidate.getTagList(),
                candidate.getImageIdList(), confidence, candidate.getSuggestedTopicPath(),
                candidate.getTargetTopicId(), candidate.getCreatedItemId(), duplicateItemId, duplicateOfCandidateId,
                questionStatus);
    }

    /** "java-notes.docx" becomes the topic "java-notes". */
    private static String fallbackTopicName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        return baseName.isBlank() ? "Imported" : baseName.strip();
    }

    private static String cleanFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "document";
        }
        String baseName = originalName.replace('\\', '/');
        baseName = baseName.substring(baseName.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (baseName.isEmpty()) {
            return "document";
        }
        return baseName.length() <= MAX_FILE_NAME_LENGTH ? baseName : baseName.substring(baseName.length() - MAX_FILE_NAME_LENGTH);
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the uploaded file", exception);
        }
    }
}
