package com.thanh.backend.common.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.auth.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Rate limiting through the real HTTP stack with one trusted proxy hop, so tests can set the client address. */
@TestPropertySource(properties = {"app.rate-limit.trusted-proxy-hops=1", "app.rate-limit.proxy-secret=" + RateLimitIT.PROXY_SECRET})
class RateLimitIT extends AbstractIntegrationTest {
    static final String PROXY_SECRET = "0123456789abcdef0123456789abcdef";
    private static final String PASSWORD = "Passw0rd!";

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwt;
    @Autowired RateLimiter limiter;

    ResponseEntity<String> post(String path, String clientIp, String body, String bearer) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Forwarded-For", clientIp);
        if (bearer != null) headers.setBearerAuth(bearer);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    ResponseEntity<String> login(String clientIp, String email, String password) {
        return post("/api/v1/auth/login", clientIp, "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password), null);
    }

    User user(String email, int serial) {
        return users.findById(jdbcTemplate.queryForObject(
                "INSERT INTO users(email,password_hash,full_name,phone,role) VALUES (?,?,?,?,'ROLE_USER') RETURNING id",
                Long.class, email, encoder.encode(PASSWORD), "Rate User", "09000008" + String.format("%02d", serial))).orElseThrow();
    }

    String errorCode(ResponseEntity<String> response) throws Exception {
        JsonNode body = json.readTree(response.getBody());
        return body.path("errorCode").asText();
    }

    @Test
    void loginIsLimitedPerClientAddressAndTellsWhenToRetry() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(login("198.51.100.1", "nobody" + i + "@example.invalid", PASSWORD).getStatusCode().value()).isEqualTo(401);
        }
        var limited = login("198.51.100.1", "nobody-last@example.invalid", PASSWORD);

        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(errorCode(limited)).isEqualTo("RATE_LIMITED");
        assertThat(Long.parseLong(limited.getHeaders().getFirst("Retry-After"))).isBetween(1L, 60L);
        // Another client address has its own counter.
        assertThat(login("198.51.100.2", "nobody@example.invalid", PASSWORD).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void repeatedWrongPasswordsBlockOnlyThatEmailFromThatClient() {
        user("victim@example.invalid", 1);
        user("other@example.invalid", 2);
        for (int i = 0; i < 5; i++) {
            assertThat(login("198.51.100.10", "victim@example.invalid", "Wrong" + i + "!").getStatusCode().value()).isEqualTo(401);
        }

        // Even the right password is refused for this (email, client) pair...
        assertThat(login("198.51.100.10", "victim@example.invalid", PASSWORD).getStatusCode().value()).isEqualTo(429);
        // ...but the owner from another address and other accounts are not locked out.
        assertThat(login("198.51.100.11", "victim@example.invalid", PASSWORD).getStatusCode().value()).isEqualTo(200);
        assertThat(login("198.51.100.10", "other@example.invalid", PASSWORD).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void aSuccessfulLoginClearsEarlierFailures() {
        user("typo@example.invalid", 3);
        for (int i = 0; i < 3; i++) login("198.51.100.20", "typo@example.invalid", "Wrong" + i + "!");
        assertThat(login("198.51.100.20", "typo@example.invalid", PASSWORD).getStatusCode().value()).isEqualTo(200);

        for (int i = 0; i < 4; i++) {
            assertThat(login("198.51.100.20", "typo@example.invalid", "Again" + i + "!").getStatusCode().value()).isEqualTo(401);
        }
    }

    @Test
    void forgedForwardedForEntriesDoNotEscapeTheLimit() {
        for (int i = 0; i < 10; i++) {
            login("1.1." + i + ".1, 203.0.113.50", "nobody@example.invalid", PASSWORD);
        }
        assertThat(login("9.9.9.9, 203.0.113.50", "nobody@example.invalid", PASSWORD).getStatusCode().value()).isEqualTo(429);
    }

    @Test
    void registrationIsLimitedPerClientAddress() {
        for (int i = 0; i < 5; i++) {
            assertThat(post("/api/v1/auth/register", "198.51.100.30", "{}", null).getStatusCode().value()).isEqualTo(400);
        }
        assertThat(post("/api/v1/auth/register", "198.51.100.30", "{}", null).getStatusCode().value()).isEqualTo(429);
    }

    @Test
    void holdingSeatsIsLimitedPerAccountNotPerAddress() {
        User alice = user("alice@example.invalid", 4);
        User bob = user("bob@example.invalid", 5);
        String aliceToken = jwt.generateAccessToken(alice);
        String bobToken = jwt.generateAccessToken(bob);
        // An empty seat list fails validation (400); it is still counted because limiting runs first.
        for (int i = 0; i < 30; i++) {
            assertThat(post("/api/v1/bookings/hold-seats", "198.51.100.40", "{\"seatIds\":[]}", aliceToken).getStatusCode().value()).isEqualTo(400);
        }
        assertThat(post("/api/v1/bookings/hold-seats", "198.51.100.40", "{\"seatIds\":[]}", aliceToken).getStatusCode().value()).isEqualTo(429);
        assertThat(post("/api/v1/bookings/hold-seats", "198.51.100.40", "{\"seatIds\":[]}", bobToken).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void aWindowEndsAndTheClientMayTryAgain() {
        assertThat(limiter.hit("ratelimit:test:window", 1, Duration.ofSeconds(1)).allowed()).isTrue();
        assertThat(limiter.hit("ratelimit:test:window", 1, Duration.ofSeconds(1)).allowed()).isFalse();

        await().atMost(Duration.ofSeconds(5)).until(() -> limiter.hit("ratelimit:test:window", 1, Duration.ofSeconds(1)).allowed());
    }

    int attempt;

    ResponseEntity<String> loginViaFrontend(String clientIp, String secret) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Client-IP", clientIp);
        if (secret != null) headers.add("X-Proxy-Secret", secret);
        return http.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"email\":\"visitor" + (++attempt) + "@example.invalid\",\"password\":\"" + PASSWORD + "\"}", headers), String.class);
    }

    @Test
    void theFrontendProxyAddressGivesEachVisitorTheirOwnCounter() {
        for (int i = 0; i < 10; i++) {
            assertThat(loginViaFrontend("203.0.113.1", PROXY_SECRET).getStatusCode().value()).isEqualTo(401);
        }
        assertThat(loginViaFrontend("203.0.113.1", PROXY_SECRET).getStatusCode().value()).isEqualTo(429);
        // Another visitor behind the same proxy is unaffected.
        assertThat(loginViaFrontend("203.0.113.2", PROXY_SECRET).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void aForgedClientAddressWithoutTheSecretDoesNotEscapeTheLimit() {
        for (int i = 0; i < 10; i++) {
            assertThat(loginViaFrontend("198.18.0." + i, "not-the-secret-not-the-secret").getStatusCode().value()).isEqualTo(401);
        }
        assertThat(loginViaFrontend("198.18.1.1", null).getStatusCode().value()).isEqualTo(429);
    }
}
