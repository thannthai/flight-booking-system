package com.thanh.backend.auth.security;

import com.thanh.backend.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Real Actuator auto-configuration and production security; no external DB/Redis needed.
@SpringBootTest(classes = ActuatorSecurityTest.TestApplication.class)
@AutoConfigureMockMvc
class ActuatorSecurityTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
    @Import({SecurityConfig.class, JwtFilter.class, JwtAuthenticationEntryPoint.class,
            JwtAccessDeniedHandler.class, ProbeController.class})
    static class TestApplication {
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/api/v1/security-probe", "/api/v1/admin/security-probe"})
        String protectedApi() {
            return "ok";
        }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/api/v1/method-security-probe")
        String methodSecuredApi() {
            return "ok";
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void healthIsUpWithoutJwtAndHidesDetails() throws Exception {
        mvc.perform(request("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    void healthIgnoresInvalidJwt() throws Exception {
        mvc.perform(request("/actuator/health").header("Authorization", "Bearer invalid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    void healthWorksUnderServletContextPath() throws Exception {
        mvc.perform(get("/booking/actuator/health")
                        .contextPath("/booking").servletPath("/actuator/health")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/env", "/actuator/configprops",
            "/actuator/beans", "/actuator/metrics", "/actuator/info", "/actuator/mappings",
            "/actuator/health/db", "/actuator/health/readiness", "/api/v1/security-probe",
            "/api/v1/admin/security-probe"})
    void otherEndpointsStillRequireAuthentication(String path) throws Exception {
        mvc.perform(request(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/configprops", "/actuator/beans",
            "/actuator/metrics", "/actuator/info", "/actuator/mappings"})
    void sensitiveActuatorEndpointsAreNotExposedEvenForAdmin(String path) throws Exception {
        authenticateAs("ADMIN");
        mvc.perform(request(path).header("Authorization", "Bearer valid"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidJwtStillRejectedOnProtectedApi() throws Exception {
        when(jwtService.validateAccessToken("invalid")).thenThrow(new JwtException("Invalid JWT"));
        mvc.perform(request("/api/v1/security-probe").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid JWT"));
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void validJwtStillAuthenticatesProtectedApi() throws Exception {
        authenticateAs("USER");
        mvc.perform(request("/api/v1/security-probe").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void userStillCannotAccessAdminApi() throws Exception {
        authenticateAs("USER");
        mvc.perform(request("/api/v1/admin/security-probe").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());
        mvc.perform(request("/api/v1/method-security-probe").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminStillCanAccessAdminApi() throws Exception {
        authenticateAs("ADMIN");
        mvc.perform(request("/api/v1/admin/security-probe").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk());
        mvc.perform(request("/api/v1/method-security-probe").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk());
    }

    private void authenticateAs(String role) {
        Claims claims = Jwts.claims().subject("test@example.com").build();
        when(jwtService.validateAccessToken("valid")).thenReturn(claims);
        when(userDetailsService.loadUserByUsername("test@example.com"))
                .thenReturn(User.withUsername("test@example.com").password("unused").roles(role).build());
    }

    private static MockHttpServletRequestBuilder request(String path) {
        return get(path).servletPath(path);
    }
}
