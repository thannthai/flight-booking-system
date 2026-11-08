package com.thanh.backend.auth.service;

import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.auth.dto.internal.AuthTokens;
import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.auth.security.JwtProvider;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class RefreshRotationIT extends AbstractIntegrationTest {
    @Autowired AuthService auth;
    @Autowired JwtService tokens;
    @Autowired JwtProvider provider;
    @Autowired UserRepository users;
    @Autowired StringRedisTemplate redis;

    private User user() {
        Long id = jdbcTemplate.queryForObject("INSERT INTO users(email,password_hash,full_name,phone,role) VALUES ('rotation@example.invalid','test-only','Rotation','0900000099','ROLE_USER') RETURNING id", Long.class);
        return users.findById(id).orElseThrow();
    }

    @Test
    void concurrentRefreshConsumesCookieExactlyOnceAndWinnerCanRotateAgain() throws Exception {
        User user = user();
        String original = provider.generateRefreshToken(user);
        tokens.storeRefreshToken(user, original);
        int contenders = 12;
        CountDownLatch ready = new CountDownLatch(contenders), start = new CountDownLatch(1);
        var futures = new ArrayList<java.util.concurrent.Future<AuthTokens>>();
        try (var pool = Executors.newFixedThreadPool(contenders)) {
            for (int i = 0; i < contenders; i++) futures.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try { return auth.refresh(original); }
                catch (AppException error) {
                    assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, error.getErrorCode());
                    return null;
                }
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var winners = new ArrayList<AuthTokens>();
            for (var result : futures) {
                AuthTokens winner = result.get(20, TimeUnit.SECONDS);
                if (winner != null) winners.add(winner);
            }
            assertEquals(1, winners.size());
            assertEquals(1, redis.keys("refresh:" + user.getId() + ":*").size());
            assertEquals(ErrorCode.REFRESH_TOKEN_INVALID,
                    assertThrows(AppException.class, () -> auth.refresh(original)).getErrorCode());
            AuthTokens next = auth.refresh(winners.getFirst().refreshToken());
            assertNotEquals(winners.getFirst().refreshToken(), next.refreshToken());
            assertEquals(1, redis.keys("refresh:" + user.getId() + ":*").size());
        } finally { start.countDown(); }
    }

    @Test
    void accessTokenCannotConsumeAStoredRefreshRecordAndLogoutRevokesCookie() {
        User user = user();
        String access = provider.generateAccessToken(user);
        tokens.storeRefreshToken(user, access);
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID,
                assertThrows(AppException.class, () -> auth.refresh(access)).getErrorCode());
        assertEquals(1, redis.keys("refresh:" + user.getId() + ":*").size());
        String refresh = provider.generateRefreshToken(user);
        tokens.storeRefreshToken(user, refresh);
        auth.logout(null, refresh);
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID,
                assertThrows(AppException.class, () -> auth.refresh(refresh)).getErrorCode());
    }
}
