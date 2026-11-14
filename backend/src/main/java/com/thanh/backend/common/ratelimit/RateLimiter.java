package com.thanh.backend.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-window counters in Redis. The first hit on a key starts its window and the key expires with it.
 * If Redis is unavailable every call allows the request: rate limiting is a secondary defence and must not
 * take login or booking down with it.
 */
@Slf4j
@Component
public class RateLimiter {
    // INCR and the expiry run in one script so a crash between them cannot leave a counter that never expires.
    private static final DefaultRedisScript<List> HIT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('TTL', KEYS[1])
            if count == 1 or ttl < 0 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
                ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);
    private static final long WARN_INTERVAL_MS = 60_000;

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static Decision allow() {
            return new Decision(true, 0);
        }
    }

    private final StringRedisTemplate redis;
    private final AtomicLong lastWarnAt = new AtomicLong();

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Counts one hit and reports whether it is within {@code limit} for the current window. */
    public Decision hit(String key, int limit, Duration window) {
        try {
            List<?> result = redis.execute(HIT, List.of(key), Long.toString(window.toSeconds()));
            long count = ((Number) result.get(0)).longValue();
            long ttl = ((Number) result.get(1)).longValue();
            return count <= limit ? Decision.allow() : new Decision(false, Math.max(1, ttl));
        } catch (RuntimeException error) {
            return failOpen(error);
        }
    }

    /** Whether the key has already reached {@code limit}, without counting a new hit. */
    public Decision peek(String key, int limit) {
        try {
            String value = redis.opsForValue().get(key);
            if (value == null || Long.parseLong(value) < limit) {
                return Decision.allow();
            }
            Long ttl = redis.getExpire(key);
            return new Decision(false, Math.max(1, ttl == null ? 1 : ttl));
        } catch (RuntimeException error) {
            return failOpen(error);
        }
    }

    public void clear(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException error) {
            failOpen(error);
        }
    }

    private Decision failOpen(RuntimeException error) {
        long now = System.currentTimeMillis();
        long last = lastWarnAt.get();
        if (now - last >= WARN_INTERVAL_MS && lastWarnAt.compareAndSet(last, now)) {
            // Class name only: Redis exception messages can include connection details.
            log.warn("Rate limiter unavailable, allowing requests: {}", error.getClass().getSimpleName());
        }
        return Decision.allow();
    }
}
