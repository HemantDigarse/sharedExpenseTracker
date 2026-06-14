package com.spreetail.expenses.auth;

import com.spreetail.expenses.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/auth/register — create a new user account</li>
 *   <li>POST /api/auth/login — authenticate and receive JWT token</li>
 * </ul>
 *
 * <p>Both endpoints are whitelisted in {@link SecurityConfig}
 * (no JWT required). All other endpoints require a valid JWT
 * in the Authorization header.
 *
 * <p>All responses use the standardized {@link ApiResponse} wrapper.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user account.
     *
     * <p>Request body is validated by @Valid — if any field fails
     * validation (e.g., blank email, short password), Spring throws
     * MethodArgumentNotValidException which is caught by
     * {@link com.spreetail.expenses.common.GlobalExceptionHandler}.
     *
     * <p>On success, returns HTTP 201 (Created) with the JWT token
     * and user profile. The user is immediately authenticated
     * (no separate login step needed).
     *
     * @param request the registration details
     * @return 201 Created with AuthResponse (token + user)
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(authResponse, "User registered successfully"));
    }

    /**
     * Authenticates a user and returns a JWT token.
     *
     * <p>On success, returns HTTP 200 with the JWT token and user profile.
     * On failure (wrong email/password), returns HTTP 401 via the
     * global exception handler.
     *
     * @param request the login credentials
     * @return 200 OK with AuthResponse (token + user)
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(
                ApiResponse.success(authResponse, "Login successful")
        );
    }
}
