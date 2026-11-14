package com.thanh.backend.common.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Fixed-window request limits, bound from {@code app.rate-limit}. */
@Getter
@Setter
@Component
@ConfigurationProperties("app.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;

    /**
     * Number of trusted reverse proxies between the internet and this application. 0 uses the socket address.
     * With N, the client is the Nth entry from the right of {@code X-Forwarded-For}; entries further left are
     * client-supplied and ignored.
     */
    private int trustedProxyHops = 0;

    /** Wrong passwords tolerated per (email, client address) before login is blocked for the window. */
    private int loginFailureLimit = 5;
    private int loginFailureWindowSeconds = 900;

    private List<Rule> rules = new ArrayList<>();

    public enum Scope { IP, USER }

    @Getter
    @Setter
    public static class Rule {
        private String name;
        private String method = "POST";
        /** Ant-style path pattern. */
        private String path;
        private Scope scope = Scope.IP;
        private int limit;
        private int windowSeconds;
    }
}
