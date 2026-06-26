package com.passlify.core.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Supplies a stub {@link JwtDecoder} so the resource-server auto-config backs off
 * and the context loads without reaching a live Keycloak issuer. Integration
 * tests set the {@code SecurityContext} directly rather than decoding real tokens.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException("Tokens are not decoded in tests");
        };
    }
}
