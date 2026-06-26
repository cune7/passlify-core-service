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
