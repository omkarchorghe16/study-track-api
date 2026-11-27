package com.studytrack.api.explanation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Explains a list of questions in the background, one at a time. */
@Component
public class ExplanationBatchRunner {
    private static final Logger log = LoggerFactory.getLogger(ExplanationBatchRunner.class);

    private final ExplanationService explanationService;

    public ExplanationBatchRunner(ExplanationService explanationService) {
        this.explanationService = explanationService;
    }

    /**
     * A question that fails is counted and skipped. A 409 (AI off, no API key) stops the run, because every
     * remaining question would fail the same way.
     */
    @Async(ExplanationConfiguration.EXPLANATION_EXECUTOR)
    public void runBatch(List<Long> questionIds, ExplanationBatchProgress progress) {
        try {
            for (Long questionId : questionIds) {
                try {
                    explanationService.generateExplanation(questionId);
                    progress.recordCompleted();
                } catch (ResponseStatusException exception) {
                    if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                        progress.stopWith(exception.getReason());
                        return;
                    }
                    progress.recordFailed();
                } catch (RuntimeException exception) {
                    progress.recordFailed();
                }
            }
            progress.finish();
            log.info("Explanation run done: questions={} completed={} failed={}", questionIds.size(),
                    progress.getCompleted(), progress.getFailed());
        } catch (RuntimeException exception) {
            progress.stopWith("The run stopped unexpectedly");
            log.error("Explanation run crashed", exception);
        }
    }
}
