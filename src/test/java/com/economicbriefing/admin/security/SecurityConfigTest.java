package com.economicbriefing.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.economicbriefing.config.AdminProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigTest {
    @Test
    void allowsCredentialsOnlyForConfiguredOrigin() {
        var config = new SecurityConfig(new AdminProperties("token", 20), new ObjectMapper(), false,
                "https://economic-beginner.onrender.com");
        var cors = config.corsConfigurationSource().getCorsConfiguration(
                new MockHttpServletRequest("OPTIONS", "/api/auth/login"));

        assertEquals("https://economic-beginner.onrender.com", cors.getAllowedOrigins().getFirst());
        assertTrue(cors.getAllowCredentials());
    }

    @Test
    void allowsLocalViteOriginsInDevelopment() {
        var config = new SecurityConfig(new AdminProperties("token", 20), new ObjectMapper(), false,
                "https://economic-beginner.onrender.com");
        var cors = config.corsConfigurationSource().getCorsConfiguration(
                new MockHttpServletRequest("OPTIONS", "/api/auth/login"));

        assertTrue(cors.getAllowedOrigins().contains("http://127.0.0.1:5175"));
        assertTrue(cors.getAllowedOrigins().contains("http://localhost:5175"));
    }
}
