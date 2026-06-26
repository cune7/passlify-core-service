package com.passlify.core.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for integration tests. Spins a real Postgres via Testcontainers and wires
 * it to Spring with {@code @ServiceConnection}, so Flyway migrates the real schema
 * and Hibernate's {@code ddl-auto=validate} verifies every entity mapping at boot.
 *
 * <p>Requires Docker. {@code disabledWithoutDocker = true} makes these tests
 * <em>skip</em> (not fail) when no Docker daemon is available, so {@code mvn install}
 * still succeeds on a machine without Docker — and they run normally once it's up.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(TestSecurityConfig.class)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
}
