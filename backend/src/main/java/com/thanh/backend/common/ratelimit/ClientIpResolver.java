package com.thanh.backend.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Identifies the calling client for rate limiting. Behind a proxy the socket address is the proxy's, so all
 * customers would share one counter; {@code X-Forwarded-For} is only used for the configured number of
 * trusted hops, because the left-hand entries can be forged by the client.
 */
@Slf4j
@Component
public class ClientIpResolver {
    private final RateLimitProperties properties;
    private final AtomicBoolean warnedAboutProxy = new AtomicBoolean();

    public ClientIpResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        int hops = properties.getTrustedProxyHops();
        String header = request.getHeader("X-Forwarded-For");
        if (hops > 0 && StringUtils.hasText(header)) {
            String[] entries = header.split(",");
            if (entries.length >= hops) {
                String candidate = entries[entries.length - hops].trim();
                if (StringUtils.hasText(candidate)) {
                    return candidate;
                }
            }
        }
        if (hops == 0 && StringUtils.hasText(header) && warnedAboutProxy.compareAndSet(false, true)) {
            // Requests arrive through a proxy but none is trusted: every customer would share the proxy's counter.
            log.warn("X-Forwarded-For is present but app.rate-limit.trusted-proxy-hops=0, so rate limits key on the "
                    + "proxy address and are shared by all clients; set RATE_LIMIT_TRUSTED_PROXY_HOPS to the number "
                    + "of trusted proxies");
        }
        return request.getRemoteAddr();
    }
}
