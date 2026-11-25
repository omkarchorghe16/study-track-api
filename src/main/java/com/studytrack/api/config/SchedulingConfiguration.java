package com.studytrack.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs (media cleanup). A cron of "-" turns a single job off. */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
