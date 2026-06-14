package com.spreetail.expenses;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Shared Expense Tracker application.
 *
 * <p>This application provides:
 * <ul>
 *   <li>Multi-currency shared expense tracking with group memberships</li>
 *   <li>CSV import pipeline with 12+ anomaly detection rules</li>
 *   <li>Balance calculation engine with time-ranged membership filtering</li>
 *   <li>Simplified settlement suggestions (minimum transactions algorithm)</li>
 *   <li>JWT-based stateless authentication</li>
 * </ul>
 *
 * <p>Architecture: Layered (Controller → Service → Repository → Entity)
 * with strict DTO separation — JPA entities are never exposed in API responses.
 *
 * @see <a href="../../resources/db/migration/">Flyway migrations</a>
 */
@SpringBootApplication
public class ExpensesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpensesApplication.class, args);
    }
}
