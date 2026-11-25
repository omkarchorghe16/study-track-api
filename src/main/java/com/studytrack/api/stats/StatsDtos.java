package com.studytrack.api.stats;

import java.time.LocalDate;
import java.util.List;

public final class StatsDtos {
    private StatsDtos() {
    }

    /**
     * Dashboard numbers for the current user. Days are calendar days in {@code app.time-zone}.
     * {@code currentStreakDays} counts days in a row with at least one review, ending today (or
     * yesterday when nothing was reviewed yet today); {@code longestStreakDays} looks back one year.
     */
    public record StatsOverviewResponse(
            long totalItems,
            long doneItems,
            long learningItems,
            long notStartedItems,
            int percentDone,
            long totalTopics,
            long completedTopics,
            long dueToday,
            long newItems,
            long reviewsToday,
            int currentStreakDays,
            int longestStreakDays,
            List<SubjectProgressResponse> subjects,
            List<DailyReviewCount> reviewsPerDay) {
    }

    /** One subject's progress; {@code dueCount}/{@code newCount} are today's flashcards. */
    public record SubjectProgressResponse(
            Long id,
            String name,
            String icon,
            boolean archived,
            long topicCount,
            long completedTopicCount,
            long itemCount,
            long doneItemCount,
            long learningItemCount,
            int percentDone,
            long dueCount,
            long newCount) {
    }

    /** Reviews on one day; the list has one entry per day of the last 30, oldest first, zeros included. */
    public record DailyReviewCount(LocalDate date, long reviewCount) {
    }
}
