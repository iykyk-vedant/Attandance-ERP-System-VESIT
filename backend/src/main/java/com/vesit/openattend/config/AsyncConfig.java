package com.vesit.openattend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    /**
     * Sends notification emails off the request thread, so an SMTP round trip never counts against
     * the attendance submission latency.
     *
     * <p>A full queue drops the email rather than throwing: the task is handed over from an
     * after-commit callback, and an exception there would fail a request whose attendance has
     * already been saved. The dropped notification stays {@code PENDING} and is still in-app.
     */
    @Bean(name = NOTIFICATION_EXECUTOR)
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notify-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("Notification email queue full ({} queued); email dropped, in-app notification kept",
                        pool.getQueue().size()));
        return executor;
    }
}
