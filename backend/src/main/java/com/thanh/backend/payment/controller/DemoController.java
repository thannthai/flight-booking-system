package com.thanh.backend.payment.controller;

import com.thanh.backend.auth.dto.response.AuthResponse;
import com.thanh.backend.common.response.ApiResponse;
import com.thanh.backend.common.utils.CookieUtils;
import com.thanh.backend.payment.service.DemoExperienceService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
public class DemoController {
    private final DemoExperienceService experience;
    private final CookieUtils cookies;
    @Value("${jwt.access-token-expiration}") private long expiration;
    @GetMapping("/info") public ApiResponse<?> info() { return ApiResponse.success(experience.info()); }
    @PostMapping("/login/{alias}") public ApiResponse<AuthResponse> login(@PathVariable String alias, HttpServletResponse response) {
        var tokens=experience.login(alias);
        cookies.setRefreshCookie(response,tokens.refreshToken());
        return ApiResponse.success(AuthResponse.builder().accessToken(tokens.accessToken()).tokenType("Bearer")
                .expiresIn(expiration/1000).user(tokens.user()).build());
    }
}
