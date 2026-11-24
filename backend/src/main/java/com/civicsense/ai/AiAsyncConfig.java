package com.civicsense.ai;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.civicsense.observability.MdcTaskDecorator;

/** Async worker executor; tests use a synchronous executor for deterministic assertions. */
@Configuration
@EnableAsync
public class AiAsyncConfig {

    @Bean(name = "aiTaskExecutor")
    TaskExecutor aiTaskExecutor(Environment environment, MdcTaskDecorator mdcTaskDecorator) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("civicsense-ai-");
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.initialize();
        return executor;
    }
}
