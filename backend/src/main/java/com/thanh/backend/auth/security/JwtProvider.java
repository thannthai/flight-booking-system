package com.thanh.backend.auth.security;

import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.enums.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

/**
 * Tầng kỹ thuật thuần JWT — chỉ tạo, ký, đọc và xác thực token.
 * Không biết Redis, không biết DB, không biết business logic.
 * Mọi nghiệp vụ liên quan đến blacklist, rotation sẽ nằm ở JwtService.
 */
@Component
public class JwtProvider {
    @Value("${jwt.secret-key}")
    private String secretKey;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    // ══════════════════════════════════════════
    // GENERATE
    // ══════════════════════════════════════════

    /**
     * Tạo Access Token cho user sau khi đăng nhập thành công.
     * Thời hạn ngắn (mặc định 15 phút) — dùng để xác thực mỗi request.
     * Payload gồm: email (sub), jti (UUID), type=ACCESS, iat, exp.
     */
    public String generateAccessToken(User user) {
        return buildToken(user.getEmail(), accessTokenExpiration, TokenType.ACCESS);
    }

    /**
     * Tạo Refresh Token cho user sau khi đăng nhập thành công.
     * Thời hạn dài (mặc định 7 ngày) — dùng để xin cấp Access Token mới
     * khi Access Token hết hạn. Được lưu trong HttpOnly Cookie phía client
     * và hash được lưu trong Redis phía server để kiểm soát rotation/revoke.
     */
    public String generateRefreshToken(User user) {
        return buildToken(user.getEmail(), refreshTokenExpiration, TokenType.REFRESH);
    }

    /**
     * Hàm nội bộ dùng chung để build JWT.
     * Mỗi token có jti (UUID) riêng — dùng để blacklist khi logout.
     * Không nhét role vào JWT vì role luôn được load mới nhất từ DB
     * thông qua CustomUserDetailsService trong mỗi request.
     */
    private String buildToken(String subject, long expiration, TokenType type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .id(UUID.randomUUID().toString())   // jti — ID duy nhất của token, dùng cho blacklist
                .claim("type", type.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(getSigningKey())
                .compact(); // nén thành chuỗi JWT
    }

    // ══════════════════════════════════════════
    // KEY
    // ══════════════════════════════════════════

    /**
     * Chuyển secret key từ chuỗi Base64 (trong application.yml)
     * thành đối tượng SecretKey dùng cho thuật toán HMAC-SHA256.
     * Private vì không class nào bên ngoài cần biết secret key.
     */
    private SecretKey getSigningKey() {
        // Dịch ngược cái chuỗi văn bản secretKey trở lại dạng mảng Byte
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);

        // Nhào nặn mảng Byte đó thành đối tượng Key chuẩn của HMAC-SHA
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ══════════════════════════════════════════
    // PARSE
    // ══════════════════════════════════════════

    /**
     * Parse JWT và trả về toàn bộ Claims (payload) bên trong.
     * JJWT tự động verify:
     *   - Chữ ký (signature) có đúng secret key không
     *   - Token đã hết hạn chưa (exp)
     * Nếu sai → throw JwtException, không cần check thêm.
     *
     * Public vì JwtService và JwtFilter dùng để lấy nhiều field
     * cùng lúc mà không phải parse JWT nhiều lần.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey()) // Dùng chìa khóa để mở
                .build()
                .parseSignedClaims(token) // Bắt đầu đọc thẻ, nếu Signature sai -> throw JwtException
                .getPayload(); // Trả về toàn bộ thông tin bên trong thẻ (gọi là Claims)
    }



    // ══════════════════════════════════════════
    // EXTRACT
    // ══════════════════════════════════════════

    /**
     * Hàm extract tổng quát — nhận vào một function chỉ định
     * muốn lấy field nào từ Claims.
     * Các hàm extractXXX bên dưới đều dùng hàm này.
     * Dùng khi chỉ cần 1 field, không cần lấy toàn bộ Claims.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(parseToken(token));
    }

    /**
     * Lấy email của user từ token (field "sub" trong JWT).
     * JwtFilter dùng để biết request này thuộc về user nào.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Lấy JWT ID (jti) — ID duy nhất của token.
     * JwtService dùng làm key khi đưa token vào Redis blacklist lúc logout:
     * "blacklist:{jti}" → true, TTL = thời gian còn lại của token.
     */
    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    /**
     * Lấy loại token: "ACCESS" hoặc "REFRESH".
     * JwtFilter dùng để đảm bảo request API chỉ chấp nhận ACCESS token,
     * không cho dùng REFRESH token để truy cập tài nguyên.
     */
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    /**
     * Lấy thời điểm hết hạn của token.
     * Dùng nội bộ trong extractRemainingTtl().
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Tính số milliseconds còn lại trước khi token hết hạn.
     * JwtService dùng để set TTL cho Redis key blacklist khi logout:
     *   SET "blacklist:{jti}" true EX {remainingTtl}
     * Sau khi token tự hết hạn, Redis cũng tự xóa key → không tốn bộ nhớ.
     * Trả về 0 nếu token đã hết hạn (không trả số âm).
     */
    public long extractRemainingTtl(String token) {
        long remaining = extractExpiration(token).getTime() - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

}