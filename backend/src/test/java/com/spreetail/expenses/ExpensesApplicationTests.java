package com.spreetail.expenses;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: verifies that the Spring application context loads
 * successfully with all configurations and beans.
 *
 * <p>The smoke test uses an in-memory H2 database so it can run without
 * requiring Docker/PostgreSQL to be available on the developer machine.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:expenses_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
class ExpensesApplicationTests {

    @Test
    void contextLoads() {
        // If this test passes, it means:
        // 1. All Spring beans are properly configured
        // 2. JPA entities can create an in-memory schema for tests
        // 3. Security, controllers, services, and repositories are wired
    }
}
