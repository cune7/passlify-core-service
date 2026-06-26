package com.passlify.core;

import com.passlify.core.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Smoke test: the application context boots against a real Postgres (Flyway
 * migrates and {@code ddl-auto=validate} passes). Requires Docker.
 */
class PasslifyCoreServiceApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
