package org.example.amartha.loan.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.Arrays;

/**
 * Async configuration for email notification.
 * <p>邮件发送使用独立线程池，旨在解耦并防止阻塞 loan 主流程。</p>
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Value("${loan.async.notif.core-pool-size:2}")
    private int corePoolSize;

    @Value("${loan.async.notif.max-pool-size:5}")
    private int maxPoolSize;

    @Value("${loan.async.notif.queue-capacity:50}")
    private int queueCapacity;

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("notif-");

        // DiscardPolicy
        executor.setRejectedExecutionHandler((r, exec) -> {
            log.error("Async notification queue is full! Task rejected. Consider increasing queue capacity or checking mail server health.");
        });

        // Graceful Shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("Uncaught async exception in method: {}. Params: {}. Error: {}",
                        method.getName(), Arrays.toString(params), ex.getMessage(), ex);
    }
}