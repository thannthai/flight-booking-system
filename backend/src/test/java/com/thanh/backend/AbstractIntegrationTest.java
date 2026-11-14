package com.thanh.backend;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "jwt.secret-key=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYQ==",
        "payos.client-id=dummy-client-id-for-test",
        "payos.api-key=dummy-api-key-for-test",
        "payos.checksum-key=dummy-checksum-key-for-test",
        "payos.return-url=http://localhost:5173/payment/success",
        "payos.cancel-url=http://localhost:5173/payment/cancel"
})
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired(required = false)
    @Qualifier("seatHoldCleanupExecutor")
    protected ThreadPoolTaskExecutor seatHoldCleanupExecutor;

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected RedisConnectionFactory redisConnectionFactory;

    @AfterEach
    void cleanUpDatabaseAndRedis() {
        drainSeatHoldCleanup();
        // Clean up Redis
        if (redisConnectionFactory != null && redisConnectionFactory.getConnection() != null) {
            redisConnectionFactory.getConnection().serverCommands().flushDb();
        }

        // Clean up Database (Truncate all tables except flyway_schema_history)
        jdbcTemplate.execute("DO $$ DECLARE " +
                "    r RECORD; " +
                "BEGIN " +
                "    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = current_schema() AND tablename != 'flyway_schema_history') LOOP " +
                "        EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' CASCADE'; " +
                "    END LOOP; " +
                "END $$;");
    }

    protected void drainSeatHoldCleanup() {
        if (seatHoldCleanupExecutor == null) return;
        try {
            // FIFO barrier submitted after the service proxy has completed its transaction.
            seatHoldCleanupExecutor.submit(() -> { }).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception error) {
            throw new AssertionError("Seat hold cleanup did not finish before fixture cleanup", error);
        }
    }
}
