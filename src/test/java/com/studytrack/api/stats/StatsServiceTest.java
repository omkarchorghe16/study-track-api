package com.studytrack.api.stats;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Test
    void currentStreakEndsTodayOrYesterday() {
        assertEquals(3, StatsService.countCurrentStreak(
                Set.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(4)), TODAY));
        assertEquals(2, StatsService.countCurrentStreak(Set.of(TODAY.minusDays(1), TODAY.minusDays(2)), TODAY));
        assertEquals(0, StatsService.countCurrentStreak(Set.of(TODAY.minusDays(2)), TODAY));
        assertEquals(0, StatsService.countCurrentStreak(Set.of(), TODAY));
    }

    @Test
    void longestStreakFindsTheLongestRun() {
        assertEquals(4, StatsService.countLongestStreak(Set.of(TODAY.minusDays(10), TODAY.minusDays(9),
                TODAY.minusDays(8), TODAY.minusDays(7), TODAY.minusDays(3), TODAY)));
        assertEquals(0, StatsService.countLongestStreak(Set.of()));
    }
}
