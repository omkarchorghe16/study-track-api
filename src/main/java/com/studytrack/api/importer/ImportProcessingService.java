package com.studytrack.api.importer;

import com.studytrack.api.importer.DocumentChunker.DocumentChunk;
import com.studytrack.api.importer.DocumentParser.DocumentParseException;
import com.studytrack.api.importer.DocumentParser.ParsedDocument;
import com.studytrack.api.importer.ExtractionResultParser.ExtractedItem;
import com.studytrack.api.importer.ExtractionResultParser.InvalidExtractionException;
import com.studytrack.api.llm.LlmClient;
import com.studytrack.api.llm.LlmDtos.LlmExtractionRequest;
import com.studytrack.api.llm.LlmDtos.LlmExtractionResult;
import com.studytrack.api.llm.LlmException;
import com.studytrack.api.llm.LlmProperties;
import com.studytrack.api.llm.LlmSettingsService;
import com.studytrack.api.media.MediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs an import in the background: parse the stored document, split it into chunks, ask the LLM for
 * items chunk by chunk and save them as PENDING candidates. A CSV is read row by row instead (no LLM):
 * every row with a question becomes a draft. When an import ends (also when it fails part-way) its drafts
 * are saved as questions at once; questions the subject already has are marked DUPLICATE. LLM calls run outside transactions; each
 * chunk's candidates and the job counters are saved together in a short transaction, so the UI's
 * progress bar moves while the job runs. Logs token usage, never document text or LLM answers.
 */
@Service
public class ImportProcessingService {
    private static final Logger log = LoggerFactory.getLogger(ImportProcessingService.class);

    private final ImportJobRepository importJobRepository;
    private final ImportCandidateRepository importCandidateRepository;
    private final MediaService mediaService;
    private final DocumentParser documentParser;
    private final DocumentChunker documentChunker;
    private final CandidateContentBuilder candidateContentBuilder;
    private final CsvQuestionReader csvQuestionReader;
    private final LlmSettingsService llmSettingsService;
    private final LlmProperties llmProperties;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public ImportProcessingService(ImportJobRepository importJobRepository,
                                   ImportCandidateRepository importCandidateRepository, MediaService mediaService,
                                   DocumentParser documentParser, DocumentChunker documentChunker,
                                   CandidateContentBuilder candidateContentBuilder,
                                   CsvQuestionReader csvQuestionReader, LlmSettingsService llmSettingsService, LlmProperties llmProperties,
                                   TransactionTemplate transactionTemplate, ApplicationEventPublisher eventPublisher) {
        this.importJobRepository = importJobRepository;
        this.importCandidateRepository = importCandidateRepository;
        this.mediaService = mediaService;
        this.documentParser = documentParser;
        this.documentChunker = documentChunker;
        this.candidateContentBuilder = candidateContentBuilder;
        this.csvQuestionReader = csvQuestionReader;
        this.llmSettingsService = llmSettingsService;
        this.llmProperties = llmProperties;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
    }

    /** A reason to stop the whole job; the message is shown to the user. */
    private static final class ImportFailure extends RuntimeException {
        ImportFailure(String message) {
            super(message);
        }
    }

    private record ChunkOutcome(List<ExtractedItem> items, boolean failed, long inputTokens, long outputTokens) {
    }

    /** Called after the job's row is committed. Jobs not in UPLOADED (already started or finished) are skipped. */
    @Async(ImportConfiguration.IMPORT_EXECUTOR)
    public void processImport(Long jobId) {
        ImportJobEntity job = importJobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != ImportJobStatus.UPLOADED) {
            return;
        }
        try {
            runImport(job);
        } catch (ImportFailure failure) {
            failJob(job, failure.getMessage());
        } catch (CsvQuestionReader.CsvFormatException exception) {
            failJob(job, "The CSV could not be read: " + exception.getMessage());
        } catch (DocumentParseException exception) {
            log.warn("Import document unreadable: jobId={} fileType={} exceptionType={}", jobId, job.getFileType(),
                    exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName());
            failJob(job, exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Import failed unexpectedly: jobId={}", jobId, exception);
            failJob(job, "The import failed unexpectedly; try again, or check the server log");
        }
    }

    private void runImport(ImportJobEntity job) {
        job.setStatus(ImportJobStatus.PARSING);
        job = saveJob(job);
        long userId = job.getUserId();
        if (job.getMediaId() == null) {
            throw new ImportFailure("The uploaded file was deleted before it could be read");
        }
        byte[] content = mediaService.readMediaBytes(userId, job.getMediaId());
        if (!job.getFileType().usesLlm()) {
            importCsvRows(job, content);
            return;
        }
        ParsedDocument document = documentParser.parseDocument(job.getFileType(), content,
                (imageBytes, altText) -> mediaService.storeImportedImage(userId, imageBytes, altText));
        List<DocumentChunk> chunks = documentChunker.splitIntoChunks(document.blocks(),
                llmProperties.maxChunkTokens(job.getLlmProvider()));
        if (chunks.isEmpty()) {
            throw new ImportFailure("No text was found in the document");
        }
        if (chunks.size() > llmProperties.maxChunksPerJob()) {
            throw new ImportFailure("The document is too long: it needs " + chunks.size()
                    + " LLM calls and the limit is " + llmProperties.maxChunksPerJob()
                    + ". Split it into smaller files.");
        }
        LlmClient client = createClient(job);
        job.setTotalChunks(chunks.size());
        job.setStatus(ImportJobStatus.EXTRACTING);
        job = saveJob(job);

        Set<Long> jobImageIds = new HashSet<>(document.imageIds());
        int sortOrder = 0;
        for (DocumentChunk chunk : chunks) {
            ChunkOutcome outcome = extractChunk(client, job, document, chunk, chunks.size());
            List<ImportCandidateEntity> candidates = new ArrayList<>();
            for (ExtractedItem item : outcome.items()) {
                candidateContentBuilder.buildCandidate(job.getId(), chunk, item, jobImageIds, sortOrder)
                        .ifPresent(candidates::add);
                sortOrder++;
            }
            job.recordChunk(outcome.failed(), outcome.inputTokens(), outcome.outputTokens());
            job = saveChunkResult(job, candidates);
        }
        if (job.getFailedChunks() == job.getTotalChunks()) {
            throw new ImportFailure("The LLM's answers could not be used for any part of the document");
        }
        job.setStatus(ImportJobStatus.READY_FOR_REVIEW);
        job = saveJob(job);
        saveDraftsAsQuestions(job.getId());
        log.info("Import done: jobId={} provider={} model={} chunks={} failedChunks={} images={} "
                        + "inputTokens={} outputTokens={}", job.getId(), job.getLlmProvider(), job.getLlmModel(),
                job.getTotalChunks(), job.getFailedChunks(), jobImageIds.size(), job.getInputTokens(),
                job.getOutputTokens());
    }

    /** Every CSV row with a question becomes a PENDING candidate; the whole file counts as one part. */
    private void importCsvRows(ImportJobEntity job, byte[] content) {
        CsvQuestionReader.CsvQuestions questions = csvQuestionReader.readQuestions(content);
        if (questions.rows().isEmpty()) {
            throw new ImportFailure("No questions were found. Put the question in column 1 and the answer in column 2.");
        }
        job.setTotalChunks(1);
        job.setStatus(ImportJobStatus.EXTRACTING);
        job = saveJob(job);
        List<ImportCandidateEntity> candidates = new ArrayList<>();
        for (CsvQuestionReader.CsvQuestionRow row : questions.rows()) {
            candidates.add(candidateContentBuilder.buildCsvCandidate(job.getId(), row, candidates.size()));
        }
        job.recordChunk(false, 0, 0);
        job = saveChunkResult(job, candidates);
        job.setStatus(ImportJobStatus.READY_FOR_REVIEW);
        job = saveJob(job);
        saveDraftsAsQuestions(job.getId());
        log.info("CSV import done: jobId={} rows={} skippedRows={}", job.getId(), candidates.size(),
                questions.skippedRows());
    }

    private LlmClient createClient(ImportJobEntity job) {
        try {
            return llmSettingsService.createClient(
                    llmSettingsService.findJobConnection(job.getUserId(), job.getLlmProvider(), job.getLlmModel()));
        } catch (LlmException exception) {
            throw new ImportFailure(exception.getMessage());
        }
    }

    /**
     * One chunk: call the LLM, and if the answer is not valid JSON ask once more with a correction.
     * Prompt-specific failures (refusal, truncated answer, invalid JSON twice) only mark the chunk as
     * failed; anything else (bad key, provider down after a retry) stops the job.
     */
    private ChunkOutcome extractChunk(LlmClient client, ImportJobEntity job, ParsedDocument document,
                                      DocumentChunk chunk, int totalChunks) {
        LlmExtractionRequest request = ExtractionPrompt.buildRequest(job.getFileName(), document.format(), chunk,
                totalChunks);
        long inputTokens = 0;
        long outputTokens = 0;
        try {
            LlmExtractionResult first = callWithRetry(client, request);
            inputTokens += first.inputTokens();
            outputTokens += first.outputTokens();
            try {
                return new ChunkOutcome(ExtractionResultParser.parseItems(first.text()), false, inputTokens,
                        outputTokens);
            } catch (InvalidExtractionException invalid) {
                log.warn("LLM answer invalid, retrying: jobId={} chunk={} problem={}", job.getId(), chunk.index(),
                        invalid.getMessage());
                LlmExtractionResult second = callWithRetry(client, request.withCorrection(first.text(),
                        ExtractionPrompt.CORRECTION_MESSAGE.formatted(invalid.getMessage())));
                inputTokens += second.inputTokens();
                outputTokens += second.outputTokens();
                return new ChunkOutcome(ExtractionResultParser.parseItems(second.text()), false, inputTokens,
                        outputTokens);
            }
        } catch (InvalidExtractionException invalidAgain) {
            log.warn("LLM answer invalid after retry, skipping chunk: jobId={} chunk={} problem={}", job.getId(),
                    chunk.index(), invalidAgain.getMessage());
        } catch (LlmException exception) {
            if (!exception.isPromptSpecific()) {
                throw new ImportFailure(exception.getMessage());
            }
            log.warn("LLM could not process chunk, skipping it: jobId={} chunk={} reason={}", job.getId(),
                    chunk.index(), exception.getReason());
        }
        return new ChunkOutcome(List.of(), true, inputTokens, outputTokens);
    }

    /** Sends the request; a rate limit, 5xx or timeout gets one more try after {@code app.llm.retry-delay}. */
    private LlmExtractionResult callWithRetry(LlmClient client, LlmExtractionRequest request) {
        try {
            return client.extract(request);
        } catch (LlmException exception) {
            if (!exception.isRetryable()) {
                throw exception;
            }
            log.warn("LLM call failed, retrying once: reason={}", exception.getReason());
            pause();
            return client.extract(request);
        }
    }

    private void pause() {
        try {
            Thread.sleep(llmProperties.retryDelay().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ImportFailure("The import was interrupted");
        }
    }

    private ImportJobEntity saveJob(ImportJobEntity job) {
        return importJobRepository.save(job);
    }

    private ImportJobEntity saveChunkResult(ImportJobEntity job, List<ImportCandidateEntity> candidates) {
        return transactionTemplate.execute(status -> {
            importCandidateRepository.saveAll(candidates);
            return importJobRepository.save(job);
        });
    }

    /** Reloads the job (the caller's copy may be stale) and ends it as FAILED. */
    private void failJob(ImportJobEntity staleJob, String message) {
        ImportJobEntity job = importJobRepository.findById(staleJob.getId()).orElse(staleJob);
        log.warn("Import failed: jobId={} provider={} model={} processedChunks={} inputTokens={} outputTokens={}",
                job.getId(), job.getLlmProvider(), job.getLlmModel(), job.getProcessedChunks(), job.getInputTokens(),
                job.getOutputTokens());
        job.markFailed(message);
        saveJob(job);
        // Questions found before the failure are kept.
        saveDraftsAsQuestions(job.getId());
    }

    /** Hands the job's drafts to {@link ImportService#saveImportDrafts}; a failure leaves them open, not lost. */
    private void saveDraftsAsQuestions(Long jobId) {
        try {
            eventPublisher.publishEvent(new ImportDraftsReadyEvent(jobId));
        } catch (RuntimeException exception) {
            log.error("Import drafts could not be saved as questions, left open: jobId={}", jobId, exception);
        }
    }
}
