package com.studytrack.api.importer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** The executor that runs imports in the background, separate from request threads. */
@Configuration
@EnableAsync
public class ImportConfiguration {
    public static final String IMPORT_EXECUTOR = "importTaskExecutor";
    private static final int QUEUE_CAPACITY = 100;

    @Bean(name = IMPORT_EXECUTOR)
    public ThreadPoolTaskExecutor importTaskExecutor(ImportProperties importProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(importProperties.workerThreads());
        executor.setMaxPoolSize(importProperties.workerThreads());
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("import-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
