package com.studytrack.api.stats;

import com.studytrack.api.config.StudyCalendar;
import com.studytrack.api.progress.ItemProgressRepository.SubjectDueCount;
import com.studytrack.api.progress.ProgressService;
import com.studytrack.api.stats.StatsDtos.DailyReviewCount;
import com.studytrack.api.stats.StatsDtos.StatsOverviewResponse;
import com.studytrack.api.stats.StatsDtos.SubjectProgressResponse;
import com.studytrack.api.subject.SubjectDtos.SubjectResponse;
import com.studytrack.api.subject.SubjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StatsService {
    static final int ACTIVITY_DAYS = 30;
    private static final int STREAK_LOOKBACK_DAYS = 366;

    private final SubjectService subjectService;
    private final ProgressService progressService;
    private final StudyCalendar studyCalendar;

    public StatsService(SubjectService subjectService, ProgressService progressService, StudyCalendar studyCalendar) {
        this.subjectService = subjectService;
        this.progressService = progressService;
        this.studyCalendar = studyCalendar;
    }

    @Transactional(readOnly = true)
    public StatsOverviewResponse getOverview() {
        Map<Long, SubjectDueCount> dueCounts = progressService.countDueItemsBySubject();
        List<SubjectProgressResponse> subjects = subjectService.listSubjects().stream()
                .map(subject -> toSubjectProgress(subject, dueCounts.get(subject.id())))
                .toList();
        long totalItems = subjects.stream().mapToLong(SubjectProgressResponse::itemCount).sum();
        long doneItems = subjects.stream().mapToLong(SubjectProgressResponse::doneItemCount).sum();
        long learningItems = subjects.stream().mapToLong(SubjectProgressResponse::learningItemCount).sum();

        LocalDate today = studyCalendar.today();
        Map<LocalDate, Long> reviewsByDay = progressService.listReviewDays(today.minusDays(STREAK_LOOKBACK_DAYS - 1))
                .stream()
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
        Set<LocalDate> reviewDays = new TreeSet<>(reviewsByDay.keySet());

        return new StatsOverviewResponse(totalItems, doneItems, learningItems, totalItems - doneItems - learningItems,
                SubjectService.percentOf(doneItems, totalItems),
                subjects.stream().mapToLong(SubjectProgressResponse::topicCount).sum(),
                subjects.stream().mapToLong(SubjectProgressResponse::completedTopicCount).sum(),
                subjects.stream().mapToLong(SubjectProgressResponse::dueCount).sum(),
                subjects.stream().mapToLong(SubjectProgressResponse::newCount).sum(),
                reviewsByDay.getOrDefault(today, 0L),
                countCurrentStreak(reviewDays, today), countLongestStreak(reviewDays),
                subjects, listDailyCounts(reviewsByDay, today));
    }

    /** Days in a row with reviews ending today, or ending yesterday when today has none yet. */
    static int countCurrentStreak(Set<LocalDate> reviewDays, LocalDate today) {
        LocalDate day = reviewDays.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (reviewDays.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    static int countLongestStreak(Set<LocalDate> reviewDays) {
        int longest = 0;
        for (LocalDate day : reviewDays) {
            if (reviewDays.contains(day.minusDays(1))) {
                continue;
            }
            int length = 0;
            LocalDate current = day;
            while (reviewDays.contains(current)) {
                length++;
                current = current.plusDays(1);
            }
            longest = Math.max(longest, length);
        }
        return longest;
    }

    private static List<DailyReviewCount> listDailyCounts(Map<LocalDate, Long> reviewsByDay, LocalDate today) {
        List<DailyReviewCount> days = new ArrayList<>(ACTIVITY_DAYS);
        for (LocalDate day = today.minusDays(ACTIVITY_DAYS - 1); !day.isAfter(today); day = day.plusDays(1)) {
            days.add(new DailyReviewCount(day, reviewsByDay.getOrDefault(day, 0L)));
        }
        return days;
    }

    private static SubjectProgressResponse toSubjectProgress(SubjectResponse subject, SubjectDueCount dueCount) {
        return new SubjectProgressResponse(subject.id(), subject.name(), subject.icon(), subject.archived(),
                subject.topicCount(), subject.completedTopicCount(), subject.itemCount(), subject.doneItemCount(),
                subject.learningItemCount(), subject.percentDone(),
                dueCount == null ? 0 : dueCount.getDueCount(), dueCount == null ? 0 : dueCount.getNewCount());
    }
}
