package com.studytrack.api.explanation;

import com.studytrack.api.catalog.CatalogService.CatalogQuestionRef;
import com.studytrack.api.explanation.ExplanationPrompt.QuestionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

/** Explains the catalog's questions in the background, one at a time and slowly enough for a free API key. */
@Component
public class CatalogExplanationPrefillRunner {
    private static final Logger log = LoggerFactory.getLogger(CatalogExplanationPrefillRunner.class);

    private final ExplanationService explanationService;
    private final ExplanationProperties explanationProperties;

    public CatalogExplanationPrefillRunner(ExplanationService explanationService,
                                           ExplanationProperties explanationProperties) {
        this.explanationService = explanationService;
        this.explanationProperties = explanationProperties;
    }

    /**
     * A failed question is counted and skipped after a longer pause. A 409 (AI off, no API key) stops the run
     * because every other question would fail too, and so does a run of failures in a row (usually the provider's
     * rate or daily limit); questions already saved stay saved, so running it again later continues where it stopped.
     */
    @Async(ExplanationConfiguration.EXPLANATION_EXECUTOR)
    public void runPrefill(List<CatalogQuestionRef> questions, PrefillProgress progress) {
        int failuresInARow = 0;
        try {
            for (CatalogQuestionRef question : questions) {
                try {
                    explanationService.writeExplanationIfMissing(
                            new QuestionContext(question.subjectName(), question.topicName(), question.questionText()));
                    progress.recordCompleted();
                    failuresInARow = 0;
                    pause(explanationProperties.prefillPause());
                } catch (ResponseStatusException exception) {
                    if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                        progress.stopWith(exception.getReason());
                        return;
                    }
                    failuresInARow = recordFailure(progress, failuresInARow);
                } catch (RuntimeException exception) {
                    failuresInARow = recordFailure(progress, failuresInARow);
                }
                if (failuresInARow >= explanationProperties.maxConsecutiveFailures()) {
                    progress.stopWith("Stopped after " + failuresInARow + " failures in a row, usually the provider's "
                            + "rate or daily limit. Saved explanations are kept; run it again later.");
                    return;
                }
            }
            progress.finish();
            log.info("Catalog pre-fill done: questions={} completed={} failed={}", questions.size(),
                    progress.getCompleted(), progress.getFailed());
        } catch (RuntimeException exception) {
            progress.stopWith("The run stopped unexpectedly");
            log.error("Catalog pre-fill crashed", exception);
        }
    }

    private int recordFailure(PrefillProgress progress, int failuresInARow) {
        progress.recordFailed();
        pause(explanationProperties.prefillFailurePause());
        return failuresInARow + 1;
    }

    private static void pause(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
