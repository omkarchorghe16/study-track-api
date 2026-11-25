package com.studytrack.api.config;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Days as the user sees them ({@code app.time-zone}). Due reviews, streaks and the activity chart all
 * count calendar days in this zone, so they change at the user's midnight rather than at UTC midnight.
 */
@Component
public class StudyCalendar {
    private final Clock clock;
    private final ZoneId zone;

    public StudyCalendar(Clock clock, AppProperties appProperties) {
        this.clock = clock;
        String timeZone = appProperties.timeZone();
        this.zone = timeZone == null || timeZone.isBlank() ? clock.getZone() : ZoneId.of(timeZone.strip());
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    public LocalDate toDate(Instant instant) {
        return LocalDate.ofInstant(instant, zone);
    }

    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    /** Reviews scheduled before this instant are due today. */
    public Instant startOfTomorrow() {
        return startOfDay(today().plusDays(1));
    }
}
