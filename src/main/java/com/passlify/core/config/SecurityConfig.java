package com.passlify.core.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server security. Validates Keycloak-issued JWTs (issuer-uri in
 * application.yml) and maps realm roles ({@code realm_access.roles}) to Spring
 * authorities {@code ROLE_*}, so {@code @PreAuthorize("hasRole('ORGANIZER')")}
 * works. Public catalog reads and provider webhooks are unauthenticated.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Public buyer-facing catalog (read-only).
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                        // Guest checkout: create an order and look it up by its (unguessable) id.
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/*/tickets").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/payment-session").permitAll()
                        // Per-ticket reads + QR/PDF: the ticket UUID (delivered to the buyer) is the capability.
                        .requestMatchers(HttpMethod.GET, "/api/v1/tickets/*", "/api/v1/tickets/*/qr", "/api/v1/tickets/*/pdf").permitAll()
                        // Provider webhooks are verified by signature, not by JWT.
                        .requestMatchers("/api/v1/webhooks/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /** Extracts Keycloak realm roles into {@code ROLE_*} authorities. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::extractRealmRoles);
        return converter;
    }

    @SuppressWarnings("unchecked")
    private static Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object roles = map.get("roles");
        if (!(roles instanceof Collection<?> roleList)) {
            return List.of();
        }
        return roleList.stream()
                .map(Object::toString)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
