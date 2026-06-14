package com.spreetail.expenses.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Health check endpoint for monitoring and deployment verification.
 *
 * <p>Provides a simple GET /api/health endpoint that returns the
 * application status. Used by:
 * <ul>
 *   <li>Docker HEALTHCHECK to verify container is running</li>
 *   <li>Load balancers (Railway, Render, AWS ALB) for health probes</li>
 *   <li>Manual verification during deployment</li>
 * </ul>
 *
 * <p>This endpoint is whitelisted in SecurityConfig (no JWT required).
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * Returns application health status.
     *
     * <p>Response includes:
     * <ul>
     *   <li>status — "UP" if the application is running</li>
     *   <li>timestamp — current server time (useful for timezone verification)</li>
     *   <li>application — application name for identification</li>
     * </ul>
     *
     * @return 200 OK with health data wrapped in ApiResponse
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> healthData = Map.of(
                "status", "UP",
                "timestamp", OffsetDateTime.now().toString(),
                "application", "Shared Expense Tracker",
                "version", "1.0.0"
        );

        return ResponseEntity.ok(
                ApiResponse.success(healthData, "Application is running")
        );
    }
}
