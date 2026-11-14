package com.thanh.backend.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

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
    private final java.util.concurrent.atomic.AtomicLong lastProxyDiagnosticAt = new java.util.concurrent.atomic.AtomicLong();

    public ClientIpResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    private static final Pattern ADDRESS = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");
    private static final int MIN_SECRET_LENGTH = 16;

    public String resolve(HttpServletRequest request) {
        String proxied = fromTrustedFrontend(request);
        if (proxied != null) {
            return proxied;
        }
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

    /** A proxy secret is configured but unusable for this request; say why, at most once every 5 minutes, without values. */
    private void diagnose(String reason) {
        long now = System.currentTimeMillis();
        long last = lastProxyDiagnosticAt.get();
        if (now - last >= 300_000 && lastProxyDiagnosticAt.compareAndSet(last, now)) {
            log.warn("RATE_LIMIT_PROXY_SECRET is set but not used for a request: {}", reason);
        }
    }

    /** The address the frontend proxy reports, accepted only with the shared secret and in address format. */
    private String fromTrustedFrontend(HttpServletRequest request) {
        String secret = properties.getProxySecret();
        String presented = request.getHeader("X-Proxy-Secret");
        String address = request.getHeader("X-Client-IP");
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            return null;
        }
        if (presented == null || address == null) {
            diagnose("the frontend proxy headers are missing (X-Client-IP present: " + (address != null)
                    + ", X-Proxy-Secret present: " + (presented != null) + ")");
            return null;
        }
        boolean matches = MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
        String candidate = address.trim();
        if (!matches) {
            diagnose("X-Proxy-Secret does not match RATE_LIMIT_PROXY_SECRET");
        } else if (!ADDRESS.matcher(candidate).matches()) {
            diagnose("X-Client-IP is not an address");
        }
        return matches && ADDRESS.matcher(candidate).matches() ? candidate : null;
    }
}
