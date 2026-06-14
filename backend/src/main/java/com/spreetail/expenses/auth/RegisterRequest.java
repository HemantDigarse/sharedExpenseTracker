package com.spreetail.expenses.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for user registration (POST /api/auth/register).
 *
 * <p>Bean Validation annotations (@NotBlank, @Email, @Size) are
 * enforced by Spring's @Valid annotation on the controller method.
 * If validation fails, {@link com.spreetail.expenses.common.GlobalExceptionHandler}
 * catches the exception and returns a 400 response with field-level errors.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    /**
     * Full name displayed in the application.
     * Maps to flatmate names: Aisha, Rohan, Priya, Meera, Sam, Dev.
     */
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 255, message = "Full name must be between 2 and 255 characters")
    private String fullName;

    /**
     * Email address — used as the unique login identifier.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    /**
     * Plain-text password — will be BCrypt-encoded before storage.
     * Minimum 6 characters for basic security.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    private String password;
}
