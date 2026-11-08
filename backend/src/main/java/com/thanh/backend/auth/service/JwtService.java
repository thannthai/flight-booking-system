package com.thanh.backend.auth.service;

import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.enums.TokenType;
import com.thanh.backend.auth.security.JwtProvider;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    // ─── Redis key prefixes ────────────────────────────────────────────────────
    // blacklist:{jti}           → "1"           TTL = còn lại của access token
    // refresh:{userId}:{hash}   → "1"           TTL 7 ngày
    // ──────────────────────────────────────────────────────────────────────────
    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String REFRESH_PREFIX   = "refresh:";
    private static final long   REFRESH_TTL_DAYS = 7;

    // ══════════════════════════════════════════════════════════════════════════
    // ACCESS TOKEN
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Validate Access Token đầy đủ:
     * 1. Parse JWT — JJWT tự verify signature + expiry
     * 2. Kiểm tra type phải là ACCESS (không cho dùng refresh token để gọi API)
     * 3. Kiểm tra jti không nằm trong blacklist Redis (bị logout rồi)
     * Trả về Claims để JwtFilter dùng luôn, không parse lại lần thứ 2.
     */
    public Claims validateAccessToken(String token) {
        // Parse JWT — nếu signature sai hoặc hết hạn, JwtException tự throw
        Claims claims = jwtProvider.parseToken(token);

        // Kiểm tra loại token — refresh token không được dùng để xác thực API
        String tokenType = claims.get("type", String.class);
        if (!TokenType.ACCESS.name().equals(tokenType)) {
            throw new JwtException("Invalid token type — expected ACCESS");
        }

        // Kiểm tra blacklist: jti (JWT ID) được blacklist khi user logout
        // Nếu key tồn tại trong Redis → token này đã bị thu hồi
        String jti = claims.getId();
        if (redisTemplate.hasKey(BLACKLIST_PREFIX + jti)) {
            throw new JwtException("Token has been revoked");
        }

        return claims;
    }

    /**
     * Đưa Access Token vào Redis blacklist khi user logout.
     * Key: "blacklist:{jti}" → "1"
     * TTL = thời gian còn lại của token — sau khi token tự hết hạn,
     * Redis cũng tự xóa key → không tốn bộ nhớ vô hạn.
     */
    public void blacklistAccessToken(String accessToken) {
        try {
            Claims claims = jwtProvider.parseToken(accessToken);
            String jti = claims.getId();
            long remainingMs = jwtProvider.extractRemainingTtl(accessToken);

            if (remainingMs > 0) {
                // Chỉ blacklist nếu token còn hạn — đã hết hạn thì không cần
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + jti,
                        "1",
                        remainingMs,
                        TimeUnit.MILLISECONDS
                );
            }
        } catch (JwtException e) {
            // Token đã hết hạn hoặc không hợp lệ — không cần làm gì
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REFRESH TOKEN
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Lưu refresh token vào Redis sau khi login/register.
     * Key format: "refresh:{userId}:{sha256(token)}" → "1"
     * Lưu userId trong key (không phải value) để có thể SCAN theo user
     * → hỗ trợ "logout tất cả thiết bị" ở tương lai.
     * Chỉ lưu hash của token, không lưu raw token — an toàn hơn nếu Redis bị breach.
     */
    public void storeRefreshToken(User user, String refreshToken) {
        String key = buildRefreshKey(user.getId(), refreshToken);
        redisTemplate.opsForValue().set(key, "1", REFRESH_TTL_DAYS, TimeUnit.DAYS);
    }

    /**
     * Validate và consume Refresh Token khi client gọi /refresh:
     * 1. Parse JWT — verify signature + chưa hết hạn
     * 2. Kiểm tra type phải là REFRESH
     * 3. Atomic DELETE key "refresh:{userId}:{hash}"; chỉ một request được dùng token
     * <p>
     * Tại sao nhận userId từ ngoài thay vì đọc từ JWT claims?
     * → JWT chỉ có email (sub), không có userId để tránh stale data.
     * → AuthService sẽ decode email từ JWT → query DB lấy User → truyền userId vào đây.
     */
    public void consumeRefreshToken(Long userId, String refreshToken) {
        Claims claims;
        try {
            claims = jwtProvider.parseToken(refreshToken);
        } catch (JwtException e) {
            // Signature sai hoặc token đã hết hạn
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // Chỉ chấp nhận token type REFRESH — không cho dùng access token ở đây
        String tokenType = claims.get("type", String.class);
        if (!TokenType.REFRESH.name().equals(tokenType)) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // Atomic consume — DELETE trả false nếu token đã được dùng hoặc revoke:
        //   - Token đã bị revoke (user logout rồi), hoặc
        //   - Token không có bản ghi hợp lệ trong Redis
        String key = buildRefreshKey(userId, refreshToken);
        if (!Boolean.TRUE.equals(redisTemplate.delete(key))) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

    }

    /**
     * Xóa refresh token khỏi Redis khi:
     * - User logout
     * - Token rotation (cấp token mới → xóa token cũ)
     */
    public void revokeRefreshToken(Long userId, String refreshToken) {
        String key = buildRefreshKey(userId, refreshToken);
        redisTemplate.delete(key);
    }

    /**
     * Xóa TẤT CẢ refresh token của một user khỏi Redis.
     * Dùng cho "Đăng xuất tất cả thiết bị" hoặc khi user đổi mật khẩu.
     * SCAN pattern "refresh:{userId}:*" để tìm hết rồi xóa.
     */
    public void revokeAllUserRefreshTokens(Long userId) {
        // SCAN an toàn hơn KEYS trong production — không block Redis
        Set<String> keys = redisTemplate.keys(REFRESH_PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Build Redis key cho refresh token.
     * Format: "refresh:{userId}:{sha256(token)}"
     * Nhờ có userId trong key, có thể dùng SCAN "refresh:{userId}:*"
     * để tìm và xóa tất cả session của một user.
     */
    private String buildRefreshKey(Long userId, String token) {
        return REFRESH_PREFIX + userId + ":" + sha256(token);
    }

    /**
     * Hash chuỗi bằng SHA-256, trả về hex string.
     * Không lưu raw token vào Redis — nếu Redis bị breach,
     * attacker chỉ có hash, không thể dùng để đăng nhập.
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 luôn có sẵn trong JVM — không bao giờ xảy ra
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

