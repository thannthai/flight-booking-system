package com.thanh.backend.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thanh.backend.common.exception.ErrorCode;
import com.thanh.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Xử lý tập trung khi request không có token hoặc token invalid.
 * Spring Security gọi class này thay vì tự trả về trang login mặc định.
 * Trả về JSON 401 đồng nhất với ApiResponse wrapper của toàn hệ thống.
 *
 * Spring gọi method này khi:
 *
 * Request
 *    |
 *    v
 * SecurityFilterChain
 *    |
 *    v
 * SecurityContext không có Authentication
 *    |
 *    v
 * AuthenticationEntryPoint.commence()
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;    // dùng để convert Java object thành JSON

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());    // HttpStatus.UNAUTHORIZED là HTTP 401
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);  // Nói cho client biết: "Response này là JSON"
        response.setCharacterEncoding("UTF-8"); // Để tiếng Việt không bị lỗi

        String message = (String) request.getAttribute("jwt_error");
        if (message == null) {
            message = ErrorCode.TOKEN_INVALID.getMessage();
        }

        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .timestamp(OffsetDateTime.now().toInstant())
                .build();

        objectMapper.writeValue(response.getWriter(), body);
    }
}