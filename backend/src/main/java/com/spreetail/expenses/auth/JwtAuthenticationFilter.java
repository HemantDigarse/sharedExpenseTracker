package com.spreetail.expenses.auth;

import com.spreetail.expenses.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT authentication filter that intercepts every HTTP request.
 *
 * <p>This filter runs ONCE per request (extends OncePerRequestFilter)
 * and checks for a valid JWT token in the Authorization header.
 *
 * <p>Flow:
 * <ol>
 *   <li>Extract "Authorization: Bearer {token}" header</li>
 *   <li>If no header or not "Bearer" prefix → skip (let SecurityConfig decide)</li>
 *   <li>Extract email from token</li>
 *   <li>Validate token (signature, expiration, email match)</li>
 *   <li>If valid → set authentication in SecurityContext</li>
 *   <li>Continue filter chain</li>
 * </ol>
 *
 * <p>This filter is registered in {@link SecurityConfig} as part of
 * the Spring Security filter chain, positioned BEFORE the
 * UsernamePasswordAuthenticationFilter.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /**
     * HTTP header prefix for JWT tokens.
     * Full header format: "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
     */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    /**
     * Core filter logic — executed once per HTTP request.
     *
     * <p>If the request has a valid JWT token, this method sets up
     * the Spring Security authentication context so that subsequent
     * filters and controllers can access the authenticated user via
     * {@code SecurityContextHolder.getContext().getAuthentication()}.
     *
     * <p>If the request has no token or an invalid token, the filter
     * chain continues without authentication. Protected endpoints
     * will then return 401 (handled by SecurityConfig).
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Step 1: Extract the Authorization header.
        final String authHeader = request.getHeader("Authorization");

        // Step 2: Check if the header exists and starts with "Bearer ".
        // If not, this is either an unauthenticated request (public endpoint)
        // or uses a different auth mechanism — skip JWT processing.
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 3: Extract the token (everything after "Bearer ").
        final String jwtToken = authHeader.substring(BEARER_PREFIX.length());

        // Step 4: Extract the email (subject) from the token.
        final String userEmail;
        try {
            userEmail = jwtService.extractEmail(jwtToken);
        } catch (Exception e) {
            // Malformed token — continue without authentication.
            // The endpoint's security config will decide whether to allow access.
            logger.debug("Failed to extract email from JWT: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // Step 5: Only authenticate if:
        //   a) We successfully extracted an email from the token
        //   b) No authentication is already set for this request
        //      (avoids re-authenticating if another filter already did it)
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Step 6: Verify the user exists in the database.
            // This ensures deleted/disabled users can't use old tokens.
            var userOptional = userRepository.findByEmail(userEmail);

            if (userOptional.isPresent() && jwtService.validateToken(jwtToken, userEmail)) {
                // Step 7: Build a Spring Security authentication token.
                // We use an empty authorities list because this app doesn't
                // use role-based access control (all authenticated users
                // have the same permissions within their groups).
                UserDetails userDetails = new User(
                        userEmail,
                        "", // password not needed for JWT auth
                        Collections.emptyList() // no roles/authorities
                );

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null, // credentials (not needed post-authentication)
                                userDetails.getAuthorities()
                        );

                // Attach request details (IP, session ID) for audit logging.
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Step 8: Set the authentication in the SecurityContext.
                // This makes the authenticated user available to controllers
                // via @AuthenticationPrincipal or SecurityContextHolder.
                SecurityContextHolder.getContext().setAuthentication(authToken);
                logger.debug("Authenticated user: {}", userEmail);
            }
        }

        // Step 9: Continue the filter chain.
        // If authentication was set, the request proceeds as authenticated.
        // If not, the request proceeds as anonymous.
        filterChain.doFilter(request, response);
    }
}
