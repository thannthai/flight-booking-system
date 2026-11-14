package com.thanh.backend.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {
    private String resolve(int hops, String forwardedFor) {
        var properties = new RateLimitProperties();
        properties.setTrustedProxyHops(hops);
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        return new ClientIpResolver(properties).resolve(request);
    }

    @Test
    void usesTheSocketAddressWhenNoProxyIsTrusted() {
        assertThat(resolve(0, "203.0.113.7")).isEqualTo("10.0.0.5");
    }

    @Test
    void takesTheEntryTheTrustedProxyAppended() {
        assertThat(resolve(1, "203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(resolve(1, "6.6.6.6, 203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(resolve(2, "6.6.6.6, 203.0.113.7, 172.16.0.1")).isEqualTo("203.0.113.7");
    }

    @Test
    void forgedLeftHandEntriesNeverChangeTheIdentity() {
        assertThat(resolve(1, "1.1.1.1, 203.0.113.7")).isEqualTo(resolve(1, "2.2.2.2, 3.3.3.3, 203.0.113.7"));
    }

    @Test
    void fallsBackToTheSocketAddressWhenTheChainIsShorterThanTheTrustedHops() {
        assertThat(resolve(3, "203.0.113.7")).isEqualTo("10.0.0.5");
        assertThat(resolve(1, null)).isEqualTo("10.0.0.5");
        assertThat(resolve(1, "  ")).isEqualTo("10.0.0.5");
    }
}
