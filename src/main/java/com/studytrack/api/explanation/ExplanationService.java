package com.studytrack.api.explanation;

import com.studytrack.api.explanation.ExplanationDtos.ExplanationResponse;
import com.studytrack.api.explanation.ExplanationGenerator.GeneratedExplanation;
import com.studytrack.api.explanation.ExplanationPrompt.QuestionContext;
import com.studytrack.api.item.ItemContent;
import com.studytrack.api.item.ItemService;
import com.studytrack.api.item.StudyItemEntity;
import com.studytrack.api.llm.LlmClient;
import com.studytrack.api.llm.LlmDtos.LlmConnectionSettings;
import com.studytrack.api.llm.LlmException;
import com.studytrack.api.llm.LlmSettingsService;
import com.studytrack.api.llm.LlmSettingsService.ImportLlmSettings;
import com.studytrack.api.subject.SubjectService;
import com.studytrack.api.topic.TopicService;
import com.studytrack.api.user.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

/**
 * Reads and writes the AI explanation of a question. The LLM call itself runs outside any database
 * transaction. Problems the user can act on are reported as 4xx so the app can show the message: 409 when
 * AI is off or has no API key, 424 when the provider failed or gave no usable answer.
 */
@Service
public class ExplanationService {
    private final QuestionExplanationRepository questionExplanationRepository;
    private final ItemService itemService;
    private final TopicService topicService;
    private final SubjectService subjectService;
    private final LlmSettingsService llmSettingsService;
    private final ExplanationGenerator explanationGenerator;
    private final CurrentUser currentUser;

    public ExplanationService(QuestionExplanationRepository questionExplanationRepository, ItemService itemService,
                              TopicService topicService, SubjectService subjectService,
                              LlmSettingsService llmSettingsService, ExplanationGenerator explanationGenerator,
                              CurrentUser currentUser) {
        this.questionExplanationRepository = questionExplanationRepository;
        this.itemService = itemService;
        this.topicService = topicService;
        this.subjectService = subjectService;
        this.llmSettingsService = llmSettingsService;
        this.explanationGenerator = explanationGenerator;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public ExplanationResponse getExplanation(Long questionId) {
        StudyItemEntity question = itemService.findOwnedItem(questionId);
        QuestionExplanationEntity explanation = questionExplanationRepository
                .findByQuestionIdAndUserId(question.getId(), currentUser.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Question " + questionId + " has no explanation yet"));
        return toResponse(explanation);
    }

    /** Asks the LLM for a new explanation and saves it, replacing an earlier one of the same question. */
    public ExplanationResponse generateExplanation(Long questionId) {
        StudyItemEntity question = itemService.findOwnedItem(questionId);
        long userId = currentUser.getUserId();
        ImportLlmSettings settings = llmSettingsService.findImportSettings(userId);
        if (!settings.enabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "AI is turned off in Settings");
        }
        QuestionContext context = new QuestionContext(
                subjectService.findOwnedSubject(question.getSubjectId()).getName(),
                topicService.findOwnedTopic(question.getTopicId()).getName(),
                readQuestionText(question), question.getAnswerPlain());
        GeneratedExplanation generated = callLlm(settings.connection(), context);
        QuestionExplanationEntity explanation = questionExplanationRepository
                .findByQuestionIdAndUserId(question.getId(), userId)
                .orElseGet(() -> new QuestionExplanationEntity(question.getId(), userId));
        explanation.replaceContent(generated.json(), settings.connection().provider(), settings.connection().model());
        return toResponse(questionExplanationRepository.save(explanation));
    }

    private GeneratedExplanation callLlm(LlmConnectionSettings connection, QuestionContext context) {
        try {
            LlmClient client = llmSettingsService.createClient(connection);
            return explanationGenerator.generateExplanation(client, context);
        } catch (LlmException exception) {
            HttpStatus status = exception.getReason() == LlmException.Reason.CONFIGURATION
                    ? HttpStatus.CONFLICT
                    : HttpStatus.FAILED_DEPENDENCY;
            throw new ResponseStatusException(status, exception.getMessage());
        }
    }

    private static String readQuestionText(StudyItemEntity question) {
        String text = ItemContent.toPlainText(question.getQuestionHtml());
        return text != null ? text : question.getTitle();
    }

    private ExplanationResponse toResponse(QuestionExplanationEntity explanation) {
        return new ExplanationResponse(explanation.getQuestionId(),
                ExplanationGenerator.readStored(explanation.getContent()), explanation.getLlmProvider(),
                explanation.getLlmModel(), explanation.getUpdatedAt());
    }
}
