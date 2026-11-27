package com.studytrack.api.explanation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.studytrack.api.explanation.ExplanationDtos.CodeExample;
import com.studytrack.api.explanation.ExplanationDtos.ExplanationContent;
import com.studytrack.api.explanation.ExplanationDtos.ExplanationStep;
import com.studytrack.api.explanation.ExplanationDtos.RealWorldExample;
import com.studytrack.api.explanation.ExplanationPrompt.QuestionContext;
import com.studytrack.api.llm.LlmClient;
import com.studytrack.api.llm.LlmDtos.LlmExtractionRequest;
import com.studytrack.api.llm.LlmDtos.LlmExtractionResult;
import com.studytrack.api.llm.LlmException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Asks the LLM to explain one question and checks the answer. When the reply is not usable JSON it asks once
 * more with a correction; a second failure is an {@link LlmException} with reason INVALID_ANSWER.
 */
@Component
public class ExplanationGenerator {
    static final int MAX_LIST_ITEMS = 8;
    static final int MAX_CODE_LENGTH = 4000;
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    /** A valid explanation and its JSON, as it is stored. */
    public record GeneratedExplanation(ExplanationContent content, String json) {
    }

    public GeneratedExplanation generateExplanation(LlmClient client, QuestionContext context) {
        LlmExtractionRequest request = ExplanationPrompt.buildRequest(context);
        LlmExtractionResult result = client.extract(request);
        try {
            return parse(result.text());
        } catch (InvalidExplanationException exception) {
            LlmExtractionRequest retry = request.withCorrection(result.text(),
                    ExplanationPrompt.CORRECTION_MESSAGE.formatted(exception.getMessage()));
            try {
                return parse(client.extract(retry).text());
            } catch (InvalidExplanationException second) {
                throw new LlmException(LlmException.Reason.INVALID_ANSWER,
                        "The AI did not return a usable explanation for this question");
            }
        }
    }

    /** Reads, cleans and re-serialises the model's JSON. */
    static GeneratedExplanation parse(String text) {
        ExplanationContent raw;
        try {
            raw = MAPPER.readValue(text, ExplanationContent.class);
        } catch (Exception exception) {
            throw new InvalidExplanationException("not valid JSON");
        }
        if (raw == null || raw.summary() == null || raw.summary().isBlank()) {
            throw new InvalidExplanationException("the summary is empty");
        }
        ExplanationContent clean = new ExplanationContent(
                raw.summary().strip(),
                blankToEmpty(raw.analogy()),
                cleanTexts(raw.keyPoints()),
                cleanSteps(raw.howItWorks()),
                cleanExample(raw.example()),
                cleanRealWorldExample(raw.realWorldExample()),
                cleanTexts(raw.commonMistakes()),
                blankToEmpty(raw.interviewTip()));
        try {
            return new GeneratedExplanation(clean, MAPPER.writeValueAsString(clean));
        } catch (Exception exception) {
            throw new IllegalStateException("Explanation cannot be written as JSON", exception);
        }
    }

    /** Reads stored JSON back into the typed content. */
    static ExplanationContent readStored(String json) {
        try {
            return MAPPER.readValue(json, ExplanationContent.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored explanation is not valid JSON", exception);
        }
    }

    private static List<String> cleanTexts(List<String> texts) {
        if (texts == null) {
            return List.of();
        }
        return texts.stream().filter(Objects::nonNull).map(String::strip).filter(text -> !text.isEmpty())
                .limit(MAX_LIST_ITEMS).toList();
    }

    private static List<ExplanationStep> cleanSteps(List<ExplanationStep> steps) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream()
                .filter(step -> step != null && !blankToEmpty(step.detail()).isEmpty())
                .map(step -> new ExplanationStep(blankToEmpty(step.title()), step.detail().strip()))
                .limit(MAX_LIST_ITEMS).toList();
    }

    private static CodeExample cleanExample(CodeExample example) {
        if (example == null || blankToEmpty(example.code()).isEmpty()) {
            return new CodeExample("", "", "", "");
        }
        String code = example.code().strip();
        return new CodeExample(blankToEmpty(example.title()), blankToEmpty(example.language()).toLowerCase(),
                code.length() > MAX_CODE_LENGTH ? code.substring(0, MAX_CODE_LENGTH) : code,
                blankToEmpty(example.explanation()));
    }

    private static RealWorldExample cleanRealWorldExample(RealWorldExample example) {
        if (example == null || blankToEmpty(example.scenario()).isEmpty()) {
            return new RealWorldExample("", "", "");
        }
        return new RealWorldExample(blankToEmpty(example.title()), example.scenario().strip(),
                blankToEmpty(example.howItApplies()));
    }

    private static String blankToEmpty(String text) {
        return text == null ? "" : text.strip();
    }

    /** The reply is not an explanation we can use; the message says why, for the correction prompt. */
    static final class InvalidExplanationException extends RuntimeException {
        InvalidExplanationException(String message) {
            super(message);
        }
    }
}
