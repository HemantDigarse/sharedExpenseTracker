package com.spreetail.expenses;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

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
        SpringApplication application = new SpringApplication(ExpensesApplication.class);
        Map<String, Object> startupProperties = datasourcePropertiesFromEnvironment();
        if (!startupProperties.isEmpty()) {
            application.addInitializers(context ->
                    context.getEnvironment().getPropertySources()
                            .addFirst(new MapPropertySource("renderDatasourceProperties", startupProperties))
            );
        }
        application.run(args);
    }

    private static Map<String, Object> datasourcePropertiesFromEnvironment() {
        Map<String, String> env = System.getenv();
        Map<String, Object> properties = new HashMap<>();

        String configuredUrl = firstPresent(
                env.get("DATABASE_JDBC_URL"),
                env.get("DATABASE_URL"),
                env.get("INTERNAL_DATABASE_URL"),
                env.get("EXTERNAL_DATABASE_URL")
        );

        String jdbcUrl = toJdbcPostgresUrl(configuredUrl);
        if (jdbcUrl == null) {
            String host = env.get("DATABASE_HOST");
            String database = env.get("DATABASE_NAME");
            if (hasText(host) && hasText(database)) {
                jdbcUrl = "jdbc:postgresql://" + host + "/" + database;
            }
        }

        if (jdbcUrl != null) {
            properties.put("spring.datasource.url", jdbcUrl);
            System.out.println("Using PostgreSQL JDBC URL: " + jdbcUrl.replaceAll("//.*@", "//***:***@"));
        }

        return properties;
    }

    private static String toJdbcPostgresUrl(String rawUrl) {
        if (!hasText(rawUrl)) {
            return null;
        }

        String uriValue = rawUrl.startsWith("jdbc:")
                ? rawUrl.substring("jdbc:".length())
                : rawUrl;

        if (!uriValue.startsWith("postgres://") && !uriValue.startsWith("postgresql://")) {
            return rawUrl.startsWith("jdbc:postgresql://") ? removeInvalidPort(rawUrl) : null;
        }

        try {
            URI uri = URI.create(uriValue.replaceFirst("^postgres://", "postgresql://"));
            String host = uri.getHost();
            String path = uri.getRawPath();
            if (!hasText(host) || !hasText(path) || "/".equals(path)) {
                return null;
            }

            StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://").append(host);
            if (uri.getPort() > 0) {
                jdbcUrl.append(":").append(uri.getPort());
            }
            jdbcUrl.append(path);

            if (hasText(uri.getRawQuery())) {
                jdbcUrl.append("?").append(uri.getRawQuery());
            }

            return jdbcUrl.toString();
        } catch (IllegalArgumentException ex) {
            return removeInvalidPort(rawUrl);
        }
    }

    private static String removeInvalidPort(String rawUrl) {
        return rawUrl.replace(":-1/", "/");
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
