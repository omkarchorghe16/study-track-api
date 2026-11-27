package com.studytrack.api.explanation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.studytrack.api.llm.LlmProvider;

import java.time.Instant;
import java.util.List;

/** Transport records of the explanation feature. */
public final class ExplanationDtos {
    private ExplanationDtos() {
    }

    /** One numbered step of "how it works". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExplanationStep(String title, String detail) {
    }

    /** A short code example; {@code code} is empty when the topic needs none. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodeExample(String title, String language, String code, String explanation) {
    }

    /** A concrete scenario from industry showing where the idea is used. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RealWorldExample(String title, String scenario, String howItApplies) {
    }

    /**
     * What the model writes and the app draws as cards. All text is plain text (never HTML); fields that do not
     * apply are empty strings or empty lists.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExplanationContent(
            String summary,
            String analogy,
            List<String> keyPoints,
            List<ExplanationStep> howItWorks,
            CodeExample example,
            RealWorldExample realWorldExample,
            List<String> commonMistakes,
            String interviewTip) {
    }

    public record ExplanationResponse(Long questionId, ExplanationContent content, LlmProvider provider, String model,
                                      Instant updatedAt) {
    }

    /** Progress of the background run for a subject. {@code state} is IDLE, RUNNING, COMPLETED or FAILED. */
    public record ExplanationBatchResponse(String state, int total, int completed, int failed, int missing,
                                           String message) {
    }
}
