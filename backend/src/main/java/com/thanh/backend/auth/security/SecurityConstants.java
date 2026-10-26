package com.thanh.backend.auth.security;

/**
 * Tập trung danh sách endpoint public vào một chỗ duy nhất.
 *
 * Cả JwtFilter (shouldNotFilter) và SecurityConfig (permitAll) đều dùng
 * constant này, đảm bảo không bao giờ bị lệch nhau khi thêm/xóa endpoint.
 */
public final class SecurityConstants {

    private SecurityConstants() {}

    public static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/flights/search",
            "/api/v1/flights/**",
            "/api/v1/airports/**",
            "/api/v1/airlines/**",
            "/api/v1/payments/webhook",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };
}
