package com.studytrack.api.progress;

import com.studytrack.api.config.StudyCalendar;
import com.studytrack.api.item.ItemDtos.ItemProgressResponse;
import com.studytrack.api.item.ItemService;
import com.studytrack.api.item.ItemStatus;
import com.studytrack.api.item.StudyItemEntity;
import com.studytrack.api.progress.ProgressDtos.DueItemResponse;
import com.studytrack.api.progress.ProgressDtos.DueItemsResponse;
import com.studytrack.api.progress.ProgressDtos.ResumeResponse;
import com.studytrack.api.progress.ProgressDtos.ReviewRequest;
import com.studytrack.api.progress.ProgressDtos.ReviewResponse;
import com.studytrack.api.progress.ProgressDtos.SaveSessionRequest;
import com.studytrack.api.progress.ProgressDtos.TopicCompletionResponse;
import com.studytrack.api.progress.ProgressDtos.UpdateItemProgressRequest;
import com.studytrack.api.progress.ReviewScheduler.ScheduledReview;
import com.studytrack.api.subject.SubjectEntity;
import com.studytrack.api.subject.SubjectService;
import com.studytrack.api.topic.TopicEntity;
import com.studytrack.api.topic.TopicService;
import com.studytrack.api.user.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Item status, topic completion, flashcard reviews and the "continue where you left off" pointer. */
@Service
public class ProgressService {
    private final ItemProgressRepository itemProgressRepository;
    private final TopicProgressRepository topicProgressRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final StudySessionRepository studySessionRepository;
    private final ReviewScheduler reviewScheduler;
    private final ItemService itemService;
    private final TopicService topicService;
    private final SubjectService subjectService;
    private final StudyCalendar studyCalendar;
    private final CurrentUser currentUser;

    public ProgressService(ItemProgressRepository itemProgressRepository,
                           TopicProgressRepository topicProgressRepository, ReviewLogRepository reviewLogRepository,
                           StudySessionRepository studySessionRepository, ReviewScheduler reviewScheduler,
                           ItemService itemService, TopicService topicService, SubjectService subjectService,
                           StudyCalendar studyCalendar, CurrentUser currentUser) {
        this.itemProgressRepository = itemProgressRepository;
        this.topicProgressRepository = topicProgressRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.studySessionRepository = studySessionRepository;
        this.reviewScheduler = reviewScheduler;
        this.itemService = itemService;
        this.topicService = topicService;
        this.subjectService = subjectService;
        this.studyCalendar = studyCalendar;
        this.currentUser = currentUser;
    }

    @Transactional
    public ItemProgressResponse updateItemProgress(Long itemId, UpdateItemProgressRequest request) {
        StudyItemEntity item = itemService.findOwnedItem(itemId);
        ItemProgressEntity progress = findOrCreateProgress(item.getId());
        if (request.status() != null) {
            progress.setStatus(request.status());
        }
        if (request.confidence() != null) {
            progress.setConfidence(request.confidence() == 0 ? null : request.confidence().shortValue());
        }
        if (request.bookmarked() != null) {
            progress.setBookmarked(request.bookmarked());
        }
        return toResponse(itemProgressRepository.save(progress));
    }

    @Transactional
    public TopicCompletionResponse markTopicComplete(Long topicId) {
        return setTopicCompletion(topicId, studyCalendar.now());
    }

    @Transactional
    public TopicCompletionResponse unmarkTopicComplete(Long topicId) {
        return setTopicCompletion(topicId, null);
    }

    /** Logs a flashcard rating and schedules the next review with {@link ReviewScheduler}. */
    @Transactional
    public ReviewResponse recordReview(ReviewRequest request) {
        StudyItemEntity item = itemService.findOwnedItem(request.itemId());
        long userId = currentUser.getUserId();
        Instant reviewedAt = studyCalendar.now();
        ItemProgressEntity progress = findOrCreateProgress(item.getId());
        int earlierSuccessRun = countSuccessRun(reviewLogRepository.findLatestResults(userId, item.getId(),
                PageRequest.of(0, ReviewScheduler.SUCCESSES_FOR_DONE - 1)));
        ScheduledReview scheduled = reviewScheduler.scheduleReview(request.result(), progress.getReviewCount(),
                earlierSuccessRun, progress.getStatus(), reviewedAt);
        progress.recordReview(reviewedAt, scheduled.nextReviewAt());
        progress.setStatus(scheduled.status());
        reviewLogRepository.save(new ReviewLogEntity(userId, item.getId(), request.result(), reviewedAt));
        ItemProgressEntity saved = itemProgressRepository.save(progress);
        return new ReviewResponse(item.getId(), request.result(), reviewedAt, scheduled.intervalDays(),
                toResponse(saved));
    }

    /** Flashcards of the subject due today (in {@code app.time-zone}), overdue first, then new items. */
    @Transactional(readOnly = true)
    public DueItemsResponse listDueItems(Long subjectId, int limit) {
        SubjectEntity subject = subjectService.findOwnedSubject(subjectId);
        long userId = currentUser.getUserId();
        Instant dueBefore = studyCalendar.startOfTomorrow();
        List<DueItemResponse> items = itemProgressRepository
                .findDueItems(subject.getId(), userId, dueBefore, PageRequest.of(0, limit)).stream()
                .map(ProgressService::toDueItem)
                .toList();
        ItemProgressRepository.SubjectDueCount counts = countDueItemsBySubject().get(subject.getId());
        long dueCount = counts == null ? 0 : counts.getDueCount();
        long newCount = counts == null ? 0 : counts.getNewCount();
        return new DueItemsResponse(subject.getId(), dueCount, newCount, items);
    }

    /** Due and new flashcard counts per subject id; subjects with neither are missing. */
    @Transactional(readOnly = true)
    public Map<Long, ItemProgressRepository.SubjectDueCount> countDueItemsBySubject() {
        return itemProgressRepository.countDueItemsBySubject(currentUser.getUserId(), studyCalendar.startOfTomorrow())
                .stream()
                .collect(Collectors.toMap(ItemProgressRepository.SubjectDueCount::getSubjectId, Function.identity()));
    }

    /** Days (in {@code app.time-zone}) of the user's reviews from {@code firstDay} on, one entry per review. */
    @Transactional(readOnly = true)
    public List<LocalDate> listReviewDays(LocalDate firstDay) {
        return reviewLogRepository.findReviewTimes(currentUser.getUserId(), studyCalendar.startOfDay(firstDay))
                .stream()
                .map(studyCalendar::toDate)
                .toList();
    }

    /** The subject's resume point, or the most recently used subject's when {@code subjectId} is null. */
    @Transactional(readOnly = true)
    public Optional<ResumeResponse> findResumePoint(Long subjectId) {
        long userId = currentUser.getUserId();
        Optional<StudySessionEntity> session = subjectId == null
                ? studySessionRepository.findFirstByUserIdOrderByUpdatedAtDescIdDesc(userId)
                : studySessionRepository.findByUserIdAndSubjectId(userId,
                subjectService.findOwnedSubject(subjectId).getId());
        return session.map(this::toResumeResponse);
    }

    @Transactional
    public ResumeResponse saveResumePoint(SaveSessionRequest request) {
        SubjectEntity subject = subjectService.findOwnedSubject(request.subjectId());
        Long topicId = request.topicId();
        if (request.itemId() != null) {
            StudyItemEntity item = itemService.findOwnedItem(request.itemId());
            if (topicId != null && !topicId.equals(item.getTopicId())) {
                throw new IllegalArgumentException("Item " + item.getId() + " is not in topic " + topicId);
            }
            topicId = item.getTopicId();
        }
        if (topicId != null && !topicService.findOwnedTopic(topicId).getSubjectId().equals(subject.getId())) {
            throw new IllegalArgumentException("Topic " + topicId + " belongs to another subject");
        }
        long userId = currentUser.getUserId();
        StudySessionEntity session = studySessionRepository.findByUserIdAndSubjectId(userId, subject.getId())
                .orElseGet(() -> new StudySessionEntity(userId, subject.getId()));
        session.moveTo(topicId, request.itemId(), studyCalendar.now());
        return toResumeResponse(studySessionRepository.save(session));
    }

    private TopicCompletionResponse setTopicCompletion(Long topicId, Instant completedAt) {
        TopicEntity topic = topicService.findOwnedTopic(topicId);
        long userId = currentUser.getUserId();
        TopicProgressEntity progress = topicProgressRepository.findByUserIdAndTopicId(userId, topic.getId())
                .orElseGet(() -> new TopicProgressEntity(userId, topic.getId()));
        progress.setCompletedAt(completedAt);
        TopicProgressEntity saved = topicProgressRepository.save(progress);
        return new TopicCompletionResponse(saved.getTopicId(), saved.isCompleted(), saved.getCompletedAt());
    }

    private ItemProgressEntity findOrCreateProgress(Long itemId) {
        long userId = currentUser.getUserId();
        return itemProgressRepository.findByUserIdAndItemId(userId, itemId)
                .orElseGet(() -> new ItemProgressEntity(userId, itemId));
    }

    private static int countSuccessRun(List<ReviewResult> latestResults) {
        int run = 0;
        for (ReviewResult result : latestResults) {
            if (!result.isSuccess()) {
                break;
            }
            run++;
        }
        return run;
    }

    /** Topic and item are dropped if they were deleted or no longer belong to the user. */
    private ResumeResponse toResumeResponse(StudySessionEntity session) {
        SubjectEntity subject = subjectService.findOwnedSubject(session.getSubjectId());
        TopicEntity topic = findTopicOrNull(session.getLastTopicId());
        StudyItemEntity item = topic == null ? null : findItemOrNull(session.getLastItemId());
        return new ResumeResponse(subject.getId(), subject.getName(), subject.getIcon(),
                topic == null ? null : topic.getId(), topic == null ? null : topic.getName(),
                item == null ? null : item.getId(), item == null ? null : item.getTitle(), session.getUpdatedAt());
    }

    private TopicEntity findTopicOrNull(Long topicId) {
        if (topicId == null) {
            return null;
        }
        try {
            return topicService.findOwnedTopic(topicId);
        } catch (NoSuchElementException exception) {
            return null;
        }
    }

    private StudyItemEntity findItemOrNull(Long itemId) {
        if (itemId == null) {
            return null;
        }
        try {
            return itemService.findOwnedItem(itemId);
        } catch (NoSuchElementException exception) {
            return null;
        }
    }

    private static ItemProgressResponse toResponse(ItemProgressEntity progress) {
        Integer confidence = progress.getConfidence() == null ? null : progress.getConfidence().intValue();
        return new ItemProgressResponse(progress.getStatus(), confidence, progress.isBookmarked(),
                progress.getReviewCount(), progress.getLastReviewedAt(), progress.getNextReviewAt());
    }

    private static DueItemResponse toDueItem(ItemProgressRepository.DueItemRow row) {
        ItemStatus status = row.getStatus() == null ? ItemStatus.NOT_STARTED : row.getStatus();
        int reviewCount = row.getReviewCount() == null ? 0 : row.getReviewCount();
        return new DueItemResponse(row.getId(), row.getTopicId(), row.getTopicName(), row.getItemType(),
                row.getTitle(), row.getQuestionHtml(), row.getAnswerHtml(), row.getNotesHtml(), row.getDifficulty(),
                status, reviewCount, row.getNextReviewAt());
    }
}
