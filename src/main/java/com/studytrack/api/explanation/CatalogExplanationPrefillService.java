package com.studytrack.api.explanation;

import com.studytrack.api.catalog.CatalogService;
import com.studytrack.api.catalog.CatalogService.CatalogQuestionRef;
import com.studytrack.api.explanation.ExplanationDtos.PrefillStatusResponse;
import com.studytrack.api.user.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Admin-only, one-off helper that writes the explanations of the catalog's questions with the AI, so users find
 * them saved instead of waiting for the AI. It only ever writes the ones that are missing, so it can be run again
 * after a stop. Progress is kept in memory (lost on restart); saved explanations are not.
 */
@Service
public class CatalogExplanationPrefillService {
    private static final String IDLE = "IDLE";

    private final CatalogService catalogService;
    private final SharedExplanationRepository sharedExplanationRepository;
    private final CatalogExplanationPrefillRunner prefillRunner;
    private final CurrentUser currentUser;
    private volatile PrefillProgress progress;

    public CatalogExplanationPrefillService(CatalogService catalogService,
                                            SharedExplanationRepository sharedExplanationRepository,
                                            CatalogExplanationPrefillRunner prefillRunner, CurrentUser currentUser) {
        this.catalogService = catalogService;
        this.sharedExplanationRepository = sharedExplanationRepository;
        this.prefillRunner = prefillRunner;
        this.currentUser = currentUser;
    }

    /** Starts the run for the catalog questions that have no saved explanation, or just reports a run in progress. */
    public synchronized PrefillStatusResponse startPrefill() {
        currentUser.requireAdmin("pre-fill the catalog explanations");
        PrefillProgress running = progress;
        List<CatalogQuestionRef> missing = findMissingQuestions();
        if (running != null && running.getState() == PrefillProgress.State.RUNNING) {
            return toResponse(running, missing.size());
        }
        PrefillProgress started = new PrefillProgress(missing.size());
        progress = started;
        if (missing.isEmpty()) {
            started.finish();
        } else {
            prefillRunner.runPrefill(missing, started);
        }
        return toResponse(started, missing.size());
    }

    public PrefillStatusResponse getPrefillStatus() {
        currentUser.requireAdmin("see the catalog explanation pre-fill");
        int missing = findMissingQuestions().size();
        PrefillProgress current = progress;
        if (current == null) {
            return new PrefillStatusResponse(IDLE, 0, 0, 0, missing, catalogService.listCatalogQuestions().size(), null);
        }
        return toResponse(current, missing);
    }

    private List<CatalogQuestionRef> findMissingQuestions() {
        return catalogService.listCatalogQuestions().stream()
                .filter(question -> !sharedExplanationRepository
                        .existsByExplanationKey(ExplanationKeys.of(question.subjectName(), question.questionText())))
                .toList();
    }

    private PrefillStatusResponse toResponse(PrefillProgress current, int missing) {
        return new PrefillStatusResponse(current.getState().name(), current.getTotal(), current.getCompleted(),
                current.getFailed(), missing, catalogService.listCatalogQuestions().size(), current.getMessage());
    }
}
