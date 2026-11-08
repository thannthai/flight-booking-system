package com.thanh.backend.auth.security;

import com.thanh.backend.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class JwtProviderTest {
    JwtProvider provider(String key) {
        var provider=new JwtProvider();
        ReflectionTestUtils.setField(provider,"secretKey",key);
        ReflectionTestUtils.setField(provider,"accessTokenExpiration",900000L);
        return provider;
    }
    @Test void opensslWrappedBase64SignsAndVerifiesWithSameKeyBytes() {
        byte[] bytes=new byte[64];new java.security.SecureRandom().nextBytes(bytes);
        var wrapped=provider(Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(bytes));
        var compact=provider(Base64.getEncoder().encodeToString(bytes));
        var token=wrapped.generateAccessToken(User.builder().email("test@example.invalid").build());
        assertEquals("test@example.invalid",compact.extractUsername(token));
    }
    @Test void invalidNonWhitespaceCharactersRemainRejected() {
        assertThrows(io.jsonwebtoken.io.DecodingException.class,()->provider("not@base64").generateAccessToken(User.builder().email("test@example.invalid").build()));
    }
}
