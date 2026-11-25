package com.studytrack.api.progress;

import com.studytrack.api.item.ItemStatus;
import com.studytrack.api.progress.ReviewScheduler.ScheduledReview;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private final ReviewScheduler scheduler = new ReviewScheduler();

    @Test
    void againComesBackTomorrowAsLearning() {
        ScheduledReview review = scheduler.scheduleReview(ReviewResult.AGAIN, 5, 2, ItemStatus.DONE, NOW);
        assertEquals(NOW.plus(Duration.ofDays(1)), review.nextReviewAt());
        assertEquals(ItemStatus.LEARNING, review.status());
    }

    @Test
    void hardComesBackInTwoDays() {
        ScheduledReview review = scheduler.scheduleReview(ReviewResult.HARD, 4, 0, ItemStatus.NOT_STARTED, NOW);
        assertEquals(2, review.intervalDays());
        assertEquals(ItemStatus.LEARNING, review.status());
    }

    @Test
    void goodAndEasyGrowWithEarlierReviews() {
        assertEquals(3, scheduler.scheduleReview(ReviewResult.GOOD, 0, 0, ItemStatus.NOT_STARTED, NOW).intervalDays());
        assertEquals(3, scheduler.scheduleReview(ReviewResult.GOOD, 1, 1, ItemStatus.LEARNING, NOW).intervalDays());
        assertEquals(12, scheduler.scheduleReview(ReviewResult.GOOD, 4, 0, ItemStatus.LEARNING, NOW).intervalDays());
        assertEquals(6, scheduler.scheduleReview(ReviewResult.EASY, 0, 0, ItemStatus.NOT_STARTED, NOW).intervalDays());
        assertEquals(18, scheduler.scheduleReview(ReviewResult.EASY, 3, 0, ItemStatus.LEARNING, NOW).intervalDays());
    }

    @Test
    void intervalIsCappedAtOneYear() {
        assertEquals(365, scheduler.scheduleReview(ReviewResult.EASY, 200, 0, ItemStatus.LEARNING, NOW).intervalDays());
    }

    @Test
    void thirdSuccessInARowMakesTheItemDone() {
        assertEquals(ItemStatus.LEARNING,
                scheduler.scheduleReview(ReviewResult.GOOD, 1, 1, ItemStatus.LEARNING, NOW).status());
        assertEquals(ItemStatus.DONE,
                scheduler.scheduleReview(ReviewResult.EASY, 2, 2, ItemStatus.LEARNING, NOW).status());
    }

    @Test
    void successKeepsAnItemMarkedDoneByHand() {
        assertEquals(ItemStatus.DONE, scheduler.scheduleReview(ReviewResult.GOOD, 0, 0, ItemStatus.DONE, NOW).status());
        assertEquals(ItemStatus.LEARNING,
                scheduler.scheduleReview(ReviewResult.HARD, 0, 0, ItemStatus.DONE, NOW).status());
    }
}
