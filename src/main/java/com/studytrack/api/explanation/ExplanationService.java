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
import java.util.concurrent.locks.ReentrantLock;

/**
 * The AI explanation of a question, shared by all users. It is saved once, found by the subject name and the
 * question text, and read from the database from then on: the AI is called only when nothing is saved. Calls
 * run one at a time (a free API key has tight rate limits, and a second user opening the same question then
 * finds the first one's result). The LLM call runs outside any database transaction. Problems the user can
 * act on are reported as 4xx so the app can show the message: 409 when AI is off or has no API key, 424 when
 * the provider failed or gave no usable answer.
 */
@Service
public class ExplanationService {
    private final SharedExplanationRepository sharedExplanationRepository;
    private final ItemService itemService;
    private final TopicService topicService;
    private final SubjectService subjectService;
    private final LlmSettingsService llmSettingsService;
    private final ExplanationGenerator explanationGenerator;
    private final CurrentUser currentUser;
    private final ReentrantLock generationLock = new ReentrantLock();

    public ExplanationService(SharedExplanationRepository sharedExplanationRepository, ItemService itemService,
                              TopicService topicService, SubjectService subjectService,
                              LlmSettingsService llmSettingsService, ExplanationGenerator explanationGenerator,
                              CurrentUser currentUser) {
        this.sharedExplanationRepository = sharedExplanationRepository;
        this.itemService = itemService;
        this.topicService = topicService;
        this.subjectService = subjectService;
        this.llmSettingsService = llmSettingsService;
        this.explanationGenerator = explanationGenerator;
        this.currentUser = currentUser;
    }

    /** The saved explanation of the question; never calls the AI. */
    @Transactional(readOnly = true)
    public ExplanationResponse getExplanation(Long questionId) {
        StudyItemEntity question = itemService.findOwnedItem(questionId);
        return sharedExplanationRepository.findByExplanationKey(computeExplanationKey(question))
                .map(explanation -> toResponse(questionId, explanation))
                .orElseThrow(() -> new NoSuchElementException("Question " + questionId + " has no explanation yet"));
    }

    /** The saved explanation, or, when there is none, one written by the AI now and saved for everyone. */
    public ExplanationResponse findOrCreateExplanation(Long questionId) {
        StudyItemEntity question = itemService.findOwnedItem(questionId);
        String key = computeExplanationKey(question);
        SharedExplanationEntity saved = sharedExplanationRepository.findByExplanationKey(key).orElse(null);
        if (saved != null) {
            return toResponse(questionId, saved);
        }
        ImportLlmSettings settings = llmSettingsService.findImportSettings(currentUser.getUserId());
        if (!settings.enabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "AI is turned off in Settings");
        }
        generationLock.lock();
        try {
            SharedExplanationEntity savedMeanwhile = sharedExplanationRepository.findByExplanationKey(key).orElse(null);
            if (savedMeanwhile != null) {
                return toResponse(questionId, savedMeanwhile);
            }
            QuestionContext context = new QuestionContext(
                    subjectService.findOwnedSubject(question.getSubjectId()).getName(),
                    topicService.findOwnedTopic(question.getTopicId()).getName(), readQuestionText(question));
            GeneratedExplanation generated = callLlm(settings.connection(), context);
            return toResponse(questionId, sharedExplanationRepository.save(new SharedExplanationEntity(key,
                    generated.json(), settings.connection().provider(), settings.connection().model())));
        } finally {
            generationLock.unlock();
        }
    }

    /** Admin only: removes the saved explanation, so the next time the question is opened it is written again. */
    @Transactional
    public void deleteExplanation(Long questionId) {
        currentUser.requireAdmin("remove a saved explanation");
        StudyItemEntity question = itemService.findOwnedItem(questionId);
        sharedExplanationRepository.deleteByExplanationKey(computeExplanationKey(question));
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

    /** Same subject name and question text (ignoring case and spacing) means the same explanation. */
    private String computeExplanationKey(StudyItemEntity question) {
        String subjectName = subjectService.findOwnedSubject(question.getSubjectId()).getName();
        return ItemContent.computeContentHash(subjectName + " > " + readQuestionText(question), null);
    }

    private static String readQuestionText(StudyItemEntity question) {
        String text = ItemContent.toPlainText(question.getQuestionHtml());
        return text != null ? text : question.getTitle();
    }

    private ExplanationResponse toResponse(Long questionId, SharedExplanationEntity explanation) {
        return new ExplanationResponse(questionId, ExplanationGenerator.readStored(explanation.getContent()),
                explanation.getLlmProvider(), explanation.getLlmModel(), explanation.getUpdatedAt());
    }
}
