package com.thanh.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class AuditConfig {

    /**
     * DateTimeProvider tùy chỉnh để Spring Data JPA Auditing hỗ trợ kiểu dữ liệu OffsetDateTime.
     * Mặc định, Spring Data Auditing dùng LocalDateTime nên sẽ bị lỗi casting khi gán vào OffsetDateTime.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}
