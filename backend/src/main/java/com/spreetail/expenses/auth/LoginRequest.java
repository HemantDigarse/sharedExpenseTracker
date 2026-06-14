package com.spreetail.expenses.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for user login (POST /api/auth/login).
 *
 * <p>Contains email and password for authentication.
 * If credentials are invalid, Spring Security throws
 * {@link org.springframework.security.authentication.BadCredentialsException}
 * which is caught by the global exception handler.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
