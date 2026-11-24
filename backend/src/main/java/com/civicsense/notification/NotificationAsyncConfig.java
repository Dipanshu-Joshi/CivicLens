package com.civicsense.notification;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.civicsense.observability.MdcTaskDecorator;

/** Post-commit notification dispatch is synchronous in tests and async in production. */
@Configuration
public class NotificationAsyncConfig {

    @Bean(name = "notificationTaskExecutor")
    TaskExecutor notificationTaskExecutor(Environment environment, MdcTaskDecorator mdcTaskDecorator) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("civicsense-notify-");
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.initialize();
        return executor;
    }
}
