package com.passlify.core.common.security;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated principal from the Keycloak JWT in the security
 * context: the {@code sub} (used as organizer/customer id) and realm roles.
 */
@Component
public class CurrentUser {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_ORGANIZER = "ORGANIZER";
    public static final String ROLE_OPERATOR = "OPERATOR";

    /** The Keycloak {@code sub}, if a JWT is present (absent for guest/public calls). */
    public Optional<String> subject() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return Optional.ofNullable(jwt.getSubject());
        }
        return Optional.empty();
    }

    public String requireSubject() {
        return subject().orElseThrow(
                () -> new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required"));
    }

    /** The {@code email} claim, if present. */
    public Optional<String> email() {
        return claim("email");
    }

    /**
     * A human display name from the token, best-effort: the {@code name} claim, else
     * {@code given_name}+{@code family_name}, else the email, else the subject.
     */
    public String displayName() {
        return claim("name")
                .or(() -> {
                    String full = (claim("given_name").orElse("") + " "
                            + claim("family_name").orElse("")).trim();
                    return full.isBlank() ? Optional.empty() : Optional.of(full);
                })
                .or(this::email)
                .orElseGet(this::requireSubject);
    }

    private Optional<String> claim(String name) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Object value = jwtAuth.getToken().getClaim(name);
            if (value != null && !value.toString().isBlank()) {
                return Optional.of(value.toString());
            }
        }
        return Optional.empty();
    }

    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        String authority = "ROLE_" + role;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (authority.equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public boolean isAdmin() {
        return hasRole(ROLE_ADMIN);
    }
}
