package com.studytrack.api.explanation;

import com.studytrack.api.explanation.ExplanationDtos.ExplanationBatchResponse;
import com.studytrack.api.subject.SubjectEntity;
import com.studytrack.api.subject.SubjectService;
import com.studytrack.api.user.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starts and reports the background run that explains every question of a subject that has no explanation yet.
 * Progress is kept in memory, so it is lost when the API restarts; saved explanations are not.
 */
@Service
public class ExplanationBatchService {
    private static final String IDLE = "IDLE";

    private final QuestionExplanationRepository questionExplanationRepository;
    private final SubjectService subjectService;
    private final ExplanationBatchRunner explanationBatchRunner;
    private final CurrentUser currentUser;
    private final Map<Long, ExplanationBatchProgress> progressBySubjectId = new ConcurrentHashMap<>();

    public ExplanationBatchService(QuestionExplanationRepository questionExplanationRepository,
                                   SubjectService subjectService, ExplanationBatchRunner explanationBatchRunner,
                                   CurrentUser currentUser) {
        this.questionExplanationRepository = questionExplanationRepository;
        this.subjectService = subjectService;
        this.explanationBatchRunner = explanationBatchRunner;
        this.currentUser = currentUser;
    }

    /** Starts the run, or just reports it when one is already running for the subject. */
    public ExplanationBatchResponse startSubjectExplanations(Long subjectId) {
        SubjectEntity subject = subjectService.findOwnedSubject(subjectId);
        synchronized (progressBySubjectId) {
            ExplanationBatchProgress running = progressBySubjectId.get(subject.getId());
            if (running != null && running.getState() == ExplanationBatchProgress.State.RUNNING) {
                return toResponse(running, countMissing(subject));
            }
            List<Long> questionIds = questionExplanationRepository
                    .findQuestionIdsWithoutExplanation(subject.getId(), currentUser.getUserId());
            ExplanationBatchProgress progress = new ExplanationBatchProgress(questionIds.size());
            progressBySubjectId.put(subject.getId(), progress);
            if (questionIds.isEmpty()) {
                progress.finish();
            } else {
                explanationBatchRunner.runBatch(questionIds, progress);
            }
            return toResponse(progress, questionIds.size());
        }
    }

    public ExplanationBatchResponse getSubjectExplanationStatus(Long subjectId) {
        SubjectEntity subject = subjectService.findOwnedSubject(subjectId);
        ExplanationBatchProgress progress = progressBySubjectId.get(subject.getId());
        int missing = countMissing(subject);
        if (progress == null) {
            return new ExplanationBatchResponse(IDLE, 0, 0, 0, missing, null);
        }
        return toResponse(progress, missing);
    }

    private int countMissing(SubjectEntity subject) {
        return questionExplanationRepository
                .findQuestionIdsWithoutExplanation(subject.getId(), currentUser.getUserId()).size();
    }

    private static ExplanationBatchResponse toResponse(ExplanationBatchProgress progress, int missing) {
        return new ExplanationBatchResponse(progress.getState().name(), progress.getTotal(), progress.getCompleted(),
                progress.getFailed(), missing, progress.getMessage());
    }
}
