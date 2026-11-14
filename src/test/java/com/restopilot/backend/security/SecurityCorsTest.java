package com.restopilot.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.core.convert.converter.Converter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityCorsTest {
    @Test
    void onlyConfiguredOriginsAreAllowedWithoutCredentials() {
        var config = new SecurityConfig(mock(JwtAuthenticationFilter.class), mock(CustomUserDetailsService.class),
                mock(ObjectProvider.class), mock(ObjectProvider.class));
        ReflectionTestUtils.setField(config, "allowedOrigins", "https://restopilot.example,http://localhost:4200");
        var source = config.corsConfigurationSource();
        var request = new MockHttpServletRequest("GET", "/api/pedidos");
        var cors = source.getCorsConfiguration(request);
        assertEquals("https://restopilot.example", cors.checkOrigin("https://restopilot.example"));
        assertNull(cors.checkOrigin("https://other.example"));
        assertEquals(false, cors.getAllowCredentials());
    }
}
