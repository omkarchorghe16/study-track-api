package com.studytrack.api.progress;

import com.studytrack.api.item.ItemStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * The simple spaced-repetition rule from the build plan; kept on its own so SM-2 can replace it.
 * <ul>
 *   <li>AGAIN: next review in 1 day, status LEARNING.</li>
 *   <li>HARD: in 2 days, status LEARNING.</li>
 *   <li>GOOD: in 3 days x max(1, earlier reviews); EASY: 6 days x max(1, earlier reviews); at most a year.</li>
 *   <li>The third GOOD/EASY in a row makes the item DONE. A GOOD/EASY on an item that is already DONE
 *       keeps it DONE (also when it was marked DONE by hand).</li>
 * </ul>
 */
@Component
public class ReviewScheduler {
    static final int SUCCESSES_FOR_DONE = 3;
    private static final long MAX_INTERVAL_DAYS = 365;

    /**
     * @param result              the rating just given
     * @param earlierReviewCount  reviews of the item before this one
     * @param earlierSuccessRun   GOOD/EASY results in a row just before this one
     * @param currentStatus       the item's status before this review
     * @param reviewedAt          when the rating was given
     */
    public ScheduledReview scheduleReview(ReviewResult result, int earlierReviewCount, int earlierSuccessRun,
                                          ItemStatus currentStatus, Instant reviewedAt) {
        long multiplier = Math.max(1, earlierReviewCount);
        long intervalDays = switch (result) {
            case AGAIN -> 1;
            case HARD -> 2;
            case GOOD -> 3 * multiplier;
            case EASY -> 6 * multiplier;
        };
        intervalDays = Math.min(intervalDays, MAX_INTERVAL_DAYS);
        ItemStatus status = nextStatus(result, earlierSuccessRun, currentStatus);
        return new ScheduledReview(reviewedAt.plus(Duration.ofDays(intervalDays)), status, intervalDays);
    }

    private ItemStatus nextStatus(ReviewResult result, int earlierSuccessRun, ItemStatus currentStatus) {
        if (!result.isSuccess()) {
            return ItemStatus.LEARNING;
        }
        if (currentStatus == ItemStatus.DONE || earlierSuccessRun + 1 >= SUCCESSES_FOR_DONE) {
            return ItemStatus.DONE;
        }
        return ItemStatus.LEARNING;
    }

    /** When the item is due next, its new status, and the interval in days. */
    public record ScheduledReview(Instant nextReviewAt, ItemStatus status, long intervalDays) {
    }
}
