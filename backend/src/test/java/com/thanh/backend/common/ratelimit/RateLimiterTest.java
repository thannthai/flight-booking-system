package com.thanh.backend.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimiterTest {
    @Test
    void anUnavailableRedisAllowsRequestsInsteadOfBreakingThem() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), ArgumentMatchers.<Object>any()))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("redis down"));
        when(redis.delete(any(String.class))).thenThrow(new RedisConnectionFailureException("redis down"));
        var limiter = new RateLimiter(redis);

        assertThat(limiter.hit("key", 1, Duration.ofSeconds(60)).allowed()).isTrue();
        assertThat(limiter.peek("key", 1).allowed()).isTrue();
        limiter.clear("key");
    }

    @Test
    void overTheLimitIsRefusedWithTheRemainingWindowAsRetryAfter() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), ArgumentMatchers.<Object>any())).thenReturn(List.of(3L, 42L));
        var decision = new RateLimiter(redis).hit("key", 2, Duration.ofSeconds(60));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(42);
    }
}
