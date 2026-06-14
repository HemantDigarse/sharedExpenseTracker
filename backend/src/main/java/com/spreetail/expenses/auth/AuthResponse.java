package com.spreetail.expenses.auth;

import com.spreetail.expenses.user.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO returned after successful authentication.
 *
 * <p>Contains the JWT token and the authenticated user's profile.
 * The token should be included in subsequent API requests as:
 * {@code Authorization: Bearer <token>}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * JWT token for authenticating subsequent requests.
     * Include in the Authorization header: "Bearer {token}"
     */
    private String token;

    /**
     * Token type — always "Bearer" for JWT.
     * Included for compliance with OAuth2 token response format.
     */
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * Authenticated user's profile (excludes passwordHash).
     */
    private UserDTO user;
}
