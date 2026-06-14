package com.spreetail.expenses;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: verifies that the Spring application context loads
 * successfully with all configurations and beans.
 *
 * <p>Note: This test requires a running PostgreSQL instance
 * (use docker compose up -d before running tests).
 * In later steps, we'll add @TestContainers for isolated test DBs.
 */
@SpringBootTest
class ExpensesApplicationTests {

    @Test
    void contextLoads() {
        // If this test passes, it means:
        // 1. All Spring beans are properly configured
        // 2. Flyway migrations ran successfully
        // 3. JPA entity mappings match the DB schema
    }
}
