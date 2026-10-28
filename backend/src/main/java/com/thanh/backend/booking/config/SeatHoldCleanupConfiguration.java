package com.thanh.backend.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class SeatHoldCleanupConfiguration {
    // Keep Boot's applicationTaskExecutor (email/payment @Async) as the default.
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskExecutor seatHoldCleanupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(128);
        executor.setThreadNamePrefix("seat-hold-cleanup-");
        // Default AbortPolicy: a full queue must never run cleanup on the caller
        // while that caller still owns its completed transaction's connection.
        return executor;
    }
}
