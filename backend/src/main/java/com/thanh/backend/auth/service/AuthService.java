package com.thanh.backend.auth.service;

import com.thanh.backend.auth.dto.internal.AuthTokens;
import com.thanh.backend.auth.dto.request.LoginRequest;
import com.thanh.backend.auth.dto.request.RegisterRequest;
import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.enums.UserRole;
import com.thanh.backend.auth.mapper.UserMapper;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.auth.security.JwtProvider;
import com.thanh.backend.auth.security.UserPrincipal;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    /**
     * Đăng ký tài khoản mới.
     * Check email + phone trùng trước khi save để tránh unique constraint error.
     * Trả AuthTokens để user login ngay sau register mà không cần bước riêng.
     */
    @Transactional
    public AuthTokens register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(ErrorCode.PHONE_EXISTED);
        }

        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.ROLE_USER);

        User saved = userRepository.save(user);
        return issueTokens(saved);
    }

    /**
     * Đăng nhập.
     * Delegate xác thực cho AuthenticationManager — Spring Security tự
     * gọi CustomUserDetailsService.loadUserByUsername() + BCrypt.matches().
     * Nếu sai credentials → BadCredentialsException → GlobalExceptionHandler bắt.
     */
    @Transactional
    public AuthTokens login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();
        return issueTokens(user);
    }

    /**
     * Tạo access token + refresh token và lưu refresh vào Redis.
     * Dùng chung cho register() và login().
     */
    private AuthTokens issueTokens(User user) {
        String accessToken = jwtProvider.generateAccessToken(user);
        String refreshToken = jwtProvider.generateRefreshToken(user);

        // Lưu hash(refreshToken) → userId vào Redis, TTL 7 ngày
        jwtService.storeRefreshToken(user, refreshToken);

        return AuthTokens.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userMapper.toResponse(user))
                .build();
    }

    /**
     * Refresh Token (Cơ chế Token Rotation).
     * 1. Parse/Verify chữ ký và hạn của Refresh Token cũ.
     * 2. Tìm user từ DB theo email (subject) trong token.
     * 3. Validate sự tồn tại của Refresh Token này trong Redis.
     * 4. Revoke (xóa) Refresh Token cũ để tránh replay attack.
     * 5. Issue cặp token mới (Access + Refresh mới) và lưu vào Redis.
     */
    @Transactional
    public AuthTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        String email;
        try {
            Claims claims = jwtProvider.parseToken(refreshToken);
            email = claims.getSubject();
        } catch (Exception e) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Kiểm tra tính hợp lệ của Refresh Token trong Redis
        jwtService.validateRefreshToken(user.getId(), refreshToken);

        // Xóa Refresh Token cũ ra khỏi Redis (Rotation)
        jwtService.revokeRefreshToken(user.getId(), refreshToken);

        // Sinh cặp token mới và lưu vào Redis
        return issueTokens(user);
    }

    /**
     * Đăng xuất.
     * 1. Đưa Access Token hiện tại vào Redis blacklist để vô hiệu hóa ngay lập tức.
     * 2. Xóa Refresh Token khỏi Redis để ngăn chặn việc xin cấp Access Token mới.
     */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        // Đưa Access Token vào blacklist để chặn các request tiếp theo dùng token này
        if (accessToken != null && !accessToken.isBlank()) {
            jwtService.blacklistAccessToken(accessToken);
        }

        // Xóa Refresh Token khỏi Redis để vô hiệu hóa phiên đăng nhập này
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                Claims claims = jwtProvider.parseToken(refreshToken);
                String email = claims.getSubject();
                userRepository.findByEmail(email).ifPresent(user -> {
                    jwtService.revokeRefreshToken(user.getId(), refreshToken);
                });
            } catch (Exception e) {
                // Token không hợp lệ hoặc hết hạn → không cần xóa
            }
        }
    }
}

