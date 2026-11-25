package com.studytrack.api.stats;

import com.studytrack.api.stats.StatsDtos.StatsOverviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "Stats", description = "Dashboard numbers")
public class StatsController {
    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    @Operation(summary = "Progress overview", description = "Totals, progress per subject, flashcards due today, streak and reviews per day for the last 30 days.")
    public StatsOverviewResponse getOverview() {
        return statsService.getOverview();
    }
}
