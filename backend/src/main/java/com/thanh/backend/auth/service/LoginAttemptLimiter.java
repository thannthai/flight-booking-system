package com.thanh.backend.auth.service;

import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import com.thanh.backend.common.ratelimit.RateLimitInterceptor;
import com.thanh.backend.common.ratelimit.RateLimitProperties;
import com.thanh.backend.common.ratelimit.RateLimiter;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Slows password guessing per (email, client address). It is deliberately not per email alone: that would let
 * anyone lock a victim out of their own account by failing logins on purpose.
 */
@Component
public class LoginAttemptLimiter {
    private final RateLimiter limiter;
    private final RateLimitProperties properties;

    public LoginAttemptLimiter(RateLimiter limiter, RateLimitProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    public String keyFor(String email, String clientIp) {
        return "ratelimit:login-failures:" + RateLimitInterceptor.sha256(email) + ":" + clientIp;
    }

    /** Throws when this pair has already failed too often in the current window. */
    public void requireNotBlocked(String key, jakarta.servlet.http.HttpServletResponse response) {
        if (!properties.isEnabled()) {
            return;
        }
        RateLimiter.Decision decision = limiter.peek(key, properties.getLoginFailureLimit());
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            throw new AppException(ErrorCode.RATE_LIMITED);
        }
    }

    public void recordFailure(String key) {
        if (properties.isEnabled()) {
            limiter.hit(key, properties.getLoginFailureLimit(), Duration.ofSeconds(properties.getLoginFailureWindowSeconds()));
        }
    }

    public void clear(String key) {
        limiter.clear(key);
    }
}
