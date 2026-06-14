package com.spreetail.expenses.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration for stateless JWT authentication.
 *
 * <p>Key configuration decisions:
 * <ul>
 *   <li>STATELESS sessions — no server-side session storage</li>
 *   <li>CSRF disabled — not needed for stateless JWT auth
 *       (CSRF attacks exploit session cookies, which we don't use)</li>
 *   <li>JWT filter runs before UsernamePasswordAuthenticationFilter</li>
 *   <li>CORS enabled for React frontend (localhost:3000 in dev)</li>
 * </ul>
 *
 * <p>Whitelisted endpoints (no JWT required):
 * <ul>
 *   <li>/api/auth/** — login and registration</li>
 *   <li>/api/health — health check for monitoring</li>
 * </ul>
 *
 * <p>All other /api/** endpoints require a valid JWT token.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final String corsAllowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8080}") String corsAllowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    /**
     * Configures the Spring Security filter chain.
     *
     * <p>This is the central security configuration. Every HTTP request
     * passes through this filter chain. The order matters:
     * <ol>
     *   <li>CORS filter (allow cross-origin from React dev server)</li>
     *   <li>CSRF disabled (stateless JWT doesn't need CSRF protection)</li>
     *   <li>URL authorization rules (whitelist vs. authenticated)</li>
     *   <li>Session policy (STATELESS — no HttpSession created)</li>
     *   <li>JWT filter (extract and validate Bearer token)</li>
     * </ol>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Enable CORS with our custom configuration.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Disable CSRF protection.
                // CSRF attacks rely on browser automatically sending session cookies.
                // Since we use JWT tokens in the Authorization header (not cookies),
                // CSRF attacks are not possible. Disabling CSRF is safe AND necessary
                // for our stateless architecture.
                .csrf(AbstractHttpConfigurer::disable)

                // Define URL-level authorization rules.
                .authorizeHttpRequests(auth -> auth
                        // PUBLIC: authentication endpoints (login, register)
                        .requestMatchers("/api/auth/**").permitAll()
                        // PUBLIC: health check endpoint for monitoring/deployment
                        .requestMatchers("/api/health").permitAll()
                        // AUTHENTICATED: everything else requires a valid JWT token
                        .anyRequest().authenticated()
                )

                // STATELESS session management.
                // Spring Security will NEVER create an HttpSession.
                // Each request must include its own JWT token.
                // This enables horizontal scaling (no session affinity needed).
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Register our JWT filter BEFORE Spring's default
                // UsernamePasswordAuthenticationFilter. This ensures the JWT
                // is processed first, and if valid, the request is authenticated
                // before any other auth filter runs.
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt password encoder bean.
     *
     * <p>BCrypt is the industry standard for password hashing:
     * <ul>
     *   <li>Includes a random salt in every hash (prevents rainbow tables)</li>
     *   <li>Configurable work factor (default 10 rounds, ~100ms per hash)</li>
     *   <li>Same password produces different hashes each time</li>
     * </ul>
     *
     * <p>Used by AuthService for:
     * <ul>
     *   <li>Registration: encode password before storing</li>
     *   <li>Login: verify provided password against stored hash</li>
     * </ul>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS configuration for cross-origin requests from React frontend.
     *
     * <p>In development, the React dev server runs on localhost:3000
     * while the Spring Boot backend runs on localhost:8080.
     * Without CORS configuration, the browser blocks cross-origin requests.
     *
     * <p>In production, update allowedOrigins to the actual frontend domain.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                Arrays.stream(corsAllowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isBlank())
                        .toList()
        );

        // Allowed HTTP methods for API calls.
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        // Allowed request headers.
        // "Authorization" is required for JWT Bearer tokens.
        // "Content-Type" is required for JSON request bodies.
        configuration.setAllowedHeaders(List.of("*"));

        // Allow credentials (cookies, authorization headers).
        // Required for the Authorization header to be included.
        configuration.setAllowCredentials(true);

        // Expose the Authorization header in responses so the
        // frontend JavaScript can read it.
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
