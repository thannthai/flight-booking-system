package com.thanh.backend.common.utils;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Tách cookie logic ra khỏi Service để Service không phụ thuộc Servlet API.
 * Controller gọi CookieUtils sau khi nhận AuthTokens từ Service.
 */
@Component
public class CookieUtils {

    @Value("${cookie.secure}")
    private boolean secure;

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String COOKIE_PATH = "/api/v1/auth";
    private static final long MAX_AGE_SECONDS = 7L * 24 * 60 * 60; // 7 ngày

    /**
     * Set HttpOnly cookie chứa refresh token.
     * SameSite=Lax (không dùng Strict) để cookie vẫn được gửi
     * sau redirect — ví dụ: redirect về từ trang thanh toán Stripe.
     */
    public void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secure)
                .path(COOKIE_PATH)
                .maxAge(MAX_AGE_SECONDS)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Xóa cookie khi logout bằng cách set maxAge=0.
     */
    public void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .path(COOKIE_PATH)
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
