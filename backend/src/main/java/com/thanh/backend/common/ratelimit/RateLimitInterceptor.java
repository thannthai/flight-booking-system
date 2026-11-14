package com.thanh.backend.common.ratelimit;

import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/** Applies the configured rules before a controller runs, so requests are counted even if validation later fails. */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimitProperties properties;
    private final RateLimiter limiter;
    private final ClientIpResolver clientIps;
    private final AntPathMatcher paths = new AntPathMatcher();

    public RateLimitInterceptor(RateLimitProperties properties, RateLimiter limiter, ClientIpResolver clientIps) {
        this.properties = properties;
        this.limiter = limiter;
        this.clientIps = clientIps;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled()) {
            return true;
        }
        for (RateLimitProperties.Rule rule : properties.getRules()) {
            if (!rule.getMethod().equalsIgnoreCase(request.getMethod())
                    || !paths.match(rule.getPath(), request.getRequestURI())) {
                continue;
            }
            String key = "ratelimit:" + rule.getName() + ":" + identity(rule, request);
            RateLimiter.Decision decision = limiter.hit(key, rule.getLimit(), Duration.ofSeconds(rule.getWindowSeconds()));
            if (!decision.allowed()) {
                response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
                throw new AppException(ErrorCode.RATE_LIMITED);
            }
        }
        return true;
    }

    private String identity(RateLimitProperties.Rule rule, HttpServletRequest request) {
        if (rule.getScope() == RateLimitProperties.Scope.USER) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken)) {
                return "user:" + sha256(authentication.getName());
            }
        }
        return "ip:" + clientIps.resolve(request);
    }

    /** Hash identifiers so that no email address is stored in Redis keys. */
    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toLowerCase().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
