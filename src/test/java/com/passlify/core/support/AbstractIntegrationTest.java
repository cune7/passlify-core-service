package com.passlify.core.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for integration tests. Spins a real Postgres via Testcontainers and wires
 * it to Spring with {@code @ServiceConnection}, so Flyway migrates the real schema
 * and Hibernate's {@code ddl-auto=validate} verifies every entity mapping at boot.
 *
 * <p><strong>Singleton container:</strong> the Postgres container is started once in a
 * static initializer and shared across every integration test class (Ryuk tears it down
 * at JVM exit). This keeps the whole suite on a single database container instead of one
 * per test class — without it, running all integration tests together spins up ~10
 * Postgres containers at once and exhausts Docker resources, causing connection failures.
 * Sharing one container also lets Spring cache a single application context, so the suite
 * runs far faster.
 *
 * <p>Requires Docker. {@code disabledWithoutDocker = true} makes these tests
 * <em>skip</em> (not fail) when no Docker daemon is available, so {@code mvn install}
 * still succeeds on a machine without Docker — and they run normally once it's up.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(TestSecurityConfig.class)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        // Start once for the whole JVM (singleton pattern). Guarded so that on a
        // machine without Docker the class still loads and @Testcontainers skips it
        // instead of failing in this initializer.
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }
}
