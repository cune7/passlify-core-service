package com.passlify.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the generated API docs.
 *
 * <p>springdoc scans the controllers automatically; this bean only adds the
 * title/description and declares the Keycloak-issued JWT bearer scheme so the
 * Swagger UI shows an <em>Authorize</em> button for exercising secured endpoints.
 *
 * <p>Spec (JSON): {@code /v3/api-docs} &nbsp;•&nbsp; Swagger UI: {@code /swagger-ui.html}
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI passlifyOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Passlify Core Service API")
                        .description("Events, ticket types, checkout, payments, ticket issuance and scanning.")
                        .version("v1.0.0")
                        .license(new License().name("Proprietary")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Keycloak-issued access token. Paste the raw JWT (no \"Bearer \" prefix).")));
    }
}
