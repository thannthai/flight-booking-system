package com.thanh.backend.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.net.URI;

/** Fail before datasource initialization; never include secret values in errors. */
public class ProductionEnvironmentValidator implements EnvironmentPostProcessor, Ordered {
    @Override
    public int getOrder() { return ConfigDataEnvironmentPostProcessor.ORDER + 1; }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod | production"))) return;
        for (String key : new String[]{"DB_URL", "DB_USERNAME", "DB_PASSWORD"}) require(environment, key);
        if (!"true".equalsIgnoreCase(environment.getProperty("cookie.secure"))) {
            throw new IllegalStateException("Production requires cookie.secure=true");
        }
        String origins = require(environment, "CORS_ALLOWED_ORIGINS");
        for (String origin : origins.split(",", -1)) https(origin.trim(), "CORS_ALLOWED_ORIGINS", true);
        https(require(environment, "PAYOS_RETURN_URL"), "PAYOS_RETURN_URL", false);
        https(require(environment, "PAYOS_CANCEL_URL"), "PAYOS_CANCEL_URL", false);
    }

    private static String require(ConfigurableEnvironment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Production requires " + key);
        }
        return value;
    }

    private static void https(String value, String key, boolean originOnly) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getHost().contains("*") || uri.getPort() == 0 || uri.getPort() > 65535
                    || (originOnly && (uri.getQuery() != null ||
                        (uri.getPath() != null && !uri.getPath().isEmpty())))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Production requires a valid HTTPS " +
                    (originOnly ? "origin in " : "URL for ") + key);
        }
    }
}
