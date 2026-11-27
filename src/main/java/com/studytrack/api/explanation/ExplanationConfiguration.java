package com.studytrack.api.explanation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** One worker thread for the catalog pre-fill: questions are explained one after another. */
@Configuration
public class ExplanationConfiguration {
    public static final String EXPLANATION_EXECUTOR = "explanationTaskExecutor";
    private static final int QUEUE_CAPACITY = 2;

    @Bean(name = EXPLANATION_EXECUTOR)
    public ThreadPoolTaskExecutor explanationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("explain-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
