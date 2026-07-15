package com.thanh.backend.auth.dto.internal;

import com.thanh.backend.auth.dto.response.UserResponse;
import lombok.Builder;

@Builder
public record AuthTokens (
        String accessToken,
        String refreshToken,
        UserResponse user
) {}
