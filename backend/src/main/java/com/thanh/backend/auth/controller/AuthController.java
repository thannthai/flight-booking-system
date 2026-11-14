package com.thanh.backend.auth.controller;

import com.thanh.backend.auth.dto.internal.AuthTokens;
import com.thanh.backend.auth.dto.request.LoginRequest;
import com.thanh.backend.auth.dto.request.RegisterRequest;
import com.thanh.backend.auth.dto.response.AuthResponse;
import com.thanh.backend.auth.service.AuthService;
import com.thanh.backend.auth.service.LoginAttemptLimiter;
import com.thanh.backend.common.ratelimit.ClientIpResolver;
import com.thanh.backend.common.response.ApiResponse;
import com.thanh.backend.common.utils.CookieUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication APIs")
public class AuthController {

    private final AuthService authService;
    private final CookieUtils cookieUtils;
    private final LoginAttemptLimiter loginAttempts;
    private final ClientIpResolver clientIps;

    /** Thời gian hết hạn access token (ms) — dùng để trả về expiresIn (giây) trong response */
    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpirationMs;

    /**
     * Đăng ký tài khoản mới.
     * Sau khi register thành công, user được login ngay (trả access token + set cookie).
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user account")
    public ApiResponse<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response
    ) {
        AuthTokens tokens = authService.register(request);
        cookieUtils.setRefreshCookie(response, tokens.refreshToken());
        return ApiResponse.success("User registered successfully", buildAuthResponse(tokens));
    }

    /**
     * Đăng nhập.
     * Access token trả trong body, refresh token set vào HttpOnly cookie.
     */
    @PostMapping("/login")
    @Operation(summary = "Login and obtain access token")
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        String failureKey = loginAttempts.keyFor(request.getEmail(), clientIps.resolve(httpRequest));
        loginAttempts.requireNotBlocked(failureKey, response);
        AuthTokens tokens;
        try {
            tokens = authService.login(request);
        } catch (BadCredentialsException e) {
            loginAttempts.recordFailure(failureKey);
            throw e;
        }
        loginAttempts.clear(failureKey);
        cookieUtils.setRefreshCookie(response, tokens.refreshToken());
        return ApiResponse.success(buildAuthResponse(tokens));
    }

    /**
     * Refresh Token (Cơ chế Token Rotation).
     * Đọc Refresh Token cũ từ cookie "refreshToken".
     * Trả về Access Token mới trong body, ghi đè Refresh Token mới vào Cookie.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token cookie")
    public ApiResponse<AuthResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        AuthTokens tokens = authService.refresh(refreshToken);
        cookieUtils.setRefreshCookie(response, tokens.refreshToken());
        return ApiResponse.success("Token refreshed successfully", buildAuthResponse(tokens));
    }

    /**
     * Đăng xuất.
     * 1. Lấy Access Token từ header Authorization.
     * 2. Lấy Refresh Token từ cookie "refreshToken".
     * 3. Blacklist Access Token & Revoke Refresh Token khỏi Redis.
     * 4. Clear cookie "refreshToken" (set maxAge = 0).
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout user and revoke tokens")
    public ApiResponse<Void> logout(
            HttpServletRequest request,
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        String authHeader = request.getHeader("Authorization");
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        authService.logout(accessToken, refreshToken);
        cookieUtils.clearRefreshCookie(response);
        return ApiResponse.success("Logged out successfully", null);
    }

    /**
     * Build AuthResponse từ AuthTokens — tách riêng để không lặp code.
     * expiresIn = 900 giây (15 phút) — lấy từ config JWT nếu cần chính xác hơn.
     */
    private AuthResponse buildAuthResponse(AuthTokens tokens) {
        return AuthResponse.builder()
                .accessToken(tokens.accessToken())
                .tokenType("Bearer")
                .expiresIn(accessTokenExpirationMs / 1000)  // ms → giây
                .user(tokens.user())
                .build();
    }
}

