package com.thanh.backend.common.config;

import com.thanh.backend.common.utils.CookieUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionEnvironmentTest {
    @Configuration(proxyBeanMethods = false)
    @Import(CookieUtils.class)
    static class CookieApplication { }

    private Map<String, String> valid(String profile) {
        var properties = new LinkedHashMap<String, String>();
        properties.put("spring.profiles.active", profile);
        properties.put("DB_URL", "jdbc:postgresql://db.example.invalid/isolated");
        properties.put("DB_USERNAME", "test-only");
        properties.put("DB_PASSWORD", "test-only-secret");
        properties.put("CORS_ALLOWED_ORIGINS", "https://app.example.invalid,https://preview.example.invalid");
        properties.put("PAYOS_RETURN_URL", "https://app.example.invalid/payment/success");
        properties.put("PAYOS_CANCEL_URL", "https://app.example.invalid/payment/cancel");
        return properties;
    }

    private ApplicationContextRunner runner(Map<String, String> values) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    // Isolate test configuration from real machine credentials/profiles.
                    context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                })
                .withPropertyValues(values.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(context -> new ProductionEnvironmentValidator().postProcessEnvironment(context.getEnvironment(), null))
                .withUserConfiguration(CookieApplication.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production"})
    void productionLoadsRealYamlAndSecuresSetAndClearCookies(String profile) {
        runner(valid(profile)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db.example.invalid/isolated");
            assertThat(context.getEnvironment().getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
            CookieUtils cookies = context.getBean(CookieUtils.class);
            var response = new MockHttpServletResponse();
            cookies.setRefreshCookie(response, "test-only-token");
            assertThat(response.getHeader("Set-Cookie")).contains("Secure", "HttpOnly", "SameSite=Lax", "Path=/api/v1/auth");
            response = new MockHttpServletResponse();
            cookies.clearRefreshCookie(response);
            assertThat(response.getHeader("Set-Cookie")).contains("Secure", "HttpOnly", "SameSite=Lax", "Path=/api/v1/auth", "Max-Age=0");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_URL", "DB_USERNAME", "DB_PASSWORD", "CORS_ALLOWED_ORIGINS", "PAYOS_RETURN_URL", "PAYOS_CANCEL_URL"})
    void productionRejectsMissingAndBlankRequiredValues(String key) {
        for (String value : new String[]{null, " "}) {
            var properties = valid("prod");
            if (value == null) properties.remove(key); else properties.put(key, value);
            runner(properties).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining(key).hasMessageNotContaining("test-only-secret");
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production"})
    void productionCannotDisableSecureCookie(String profile) {
        var properties = valid(profile);
        for (String key : new String[]{"cookie.secure", "COOKIE_SECURE"}) {
            properties.put(key, "false");
            runner(properties).run(context -> assertThat(context).hasFailed());
            properties.remove(key);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://app.example.invalid", "https://user:secret@app.example.invalid", "https://app.example.invalid/#fragment", "*"})
    void productionRejectsInsecureOrMalformedWebAddresses(String value) {
        for (String key : new String[]{"CORS_ALLOWED_ORIGINS", "PAYOS_RETURN_URL", "PAYOS_CANCEL_URL"}) {
            var properties = valid("prod");properties.put(key, value);
            runner(properties).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining(key).hasMessageNotContaining("user:secret");
            });
        }
    }

    @Test
    void corsRequiresAnOriginRatherThanAUrlPath() {
        var properties = valid("prod");properties.put("CORS_ALLOWED_ORIGINS", "https://app.example.invalid/path");
        runner(properties).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production"})
    void productionDisablesApiDocsUnlessExplicitlyEnabled(String profile) {
        runner(valid(profile)).run(context -> {
            assertThat(context.getEnvironment().getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
            assertThat(context.getEnvironment().getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
        });
        var enabled = valid(profile);
        enabled.put("SWAGGER_ENABLED", "true");
        runner(enabled).run(context -> {
            assertThat(context.getEnvironment().getProperty("springdoc.api-docs.enabled", Boolean.class)).isTrue();
            assertThat(context.getEnvironment().getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isTrue();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "dev", "demo", "test"})
    void localProfilesKeepApiDocsAvailable(String profile) {
        runner(Map.of("spring.profiles.active", profile)).run(context ->
                assertThat(context.getEnvironment().getProperty("springdoc.api-docs.enabled", Boolean.class, true)).isTrue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "dev", "demo", "test"})
    void localProfilesKeepHttpDevelopmentCookieWithoutProductionSecrets(String profile) {
        runner(Map.of("spring.profiles.active", profile)).run(context -> {
            assertThat(context).hasNotFailed();
            var response = new MockHttpServletResponse();
            context.getBean(CookieUtils.class).setRefreshCookie(response, "test-only-token");
            assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure");
        });
    }

    @Test
    void validatorIsRegisteredBeforeBeanCreation() {
        assertThat(SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class, getClass().getClassLoader()))
                .contains(ProductionEnvironmentValidator.class.getName());
    }
}
