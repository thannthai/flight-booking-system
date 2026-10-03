package com.thanh.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/flight_booking",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.temp.use_jdbc_metadata_defaults=false",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "jwt.secret-key=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYQ==",
        "payos.client-id=dummy-client-id-for-test",
        "payos.api-key=dummy-api-key-for-test",
        "payos.checksum-key=dummy-checksum-key-for-test",
        "payos.return-url=http://localhost:5173/payment/success",
        "payos.cancel-url=http://localhost:5173/payment/cancel"
})
@ActiveProfiles("test")
class BackendApplicationTests {

    @Test
    void contextLoads() {
        // Verify Spring context boots successfully with all beans wired
    }

}
