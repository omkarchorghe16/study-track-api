package com.studytrack.api.explanation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** One worker thread: questions are explained one after another, which keeps within provider rate limits. */
@Configuration
public class ExplanationConfiguration {
    public static final String EXPLANATION_EXECUTOR = "explanationTaskExecutor";
    private static final int QUEUE_CAPACITY = 20;

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
