package com.spreetail.expenses.auth;

import com.spreetail.expenses.common.BusinessRuleException;
import com.spreetail.expenses.user.User;
import com.spreetail.expenses.user.UserDTO;
import com.spreetail.expenses.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service handling user registration and authentication.
 *
 * <p>Registration flow:
 * <ol>
 *   <li>Validate request (handled by @Valid in controller)</li>
 *   <li>Check email uniqueness</li>
 *   <li>BCrypt-encode the password</li>
 *   <li>Save user to database</li>
 *   <li>Generate JWT token</li>
 *   <li>Return AuthResponse (token + user profile)</li>
 * </ol>
 *
 * <p>Login flow:
 * <ol>
 *   <li>Look up user by email</li>
 *   <li>Verify password against BCrypt hash</li>
 *   <li>Generate JWT token</li>
 *   <li>Return AuthResponse (token + user profile)</li>
 * </ol>
 *
 * <p>Write operations use @Transactional to ensure atomicity.
 * If any step fails during registration, the entire operation
 * rolls back (user is not partially created).
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Registers a new user account.
     *
     * <p>@Transactional ensures that if the database write fails
     * (e.g., unique constraint violation due to a race condition),
     * the entire operation is rolled back. Spring's default rollback
     * policy rolls back on any RuntimeException.
     *
     * @param request the registration details (validated by @Valid)
     * @return AuthResponse containing JWT token and user profile
     * @throws BusinessRuleException if the email is already registered
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        logger.info("Registering new user with email: {}", request.getEmail());

        // Check if email is already taken.
        // existsByEmail() is more efficient than findByEmail().isPresent()
        // because it generates an EXISTS SQL query (stops at first match).
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessRuleException(
                    "Email '" + request.getEmail() + "' is already registered"
            );
        }

        // Build the user entity.
        // Password is BCrypt-encoded BEFORE storage — the raw password
        // is never persisted. BCrypt includes a random salt in each hash,
        // so the same password produces different hashes each time.
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        // Save to database. JPA @PrePersist sets created_at and updated_at.
        User savedUser = userRepository.save(user);
        logger.info("User registered successfully: {} (ID: {})", savedUser.getEmail(), savedUser.getId());

        // Generate JWT token for immediate login after registration.
        // The user doesn't need to log in separately after registering.
        String token = jwtService.generateToken(savedUser.getEmail());

        return AuthResponse.builder()
                .token(token)
                .user(UserDTO.fromEntity(savedUser))
                .build();
    }

    /**
     * Authenticates a user with email and password.
     *
     * <p>Read-only operation — no @Transactional needed (Spring Data
     * wraps the findByEmail query in its own transaction).
     *
     * @param request the login credentials
     * @return AuthResponse containing JWT token and user profile
     * @throws BadCredentialsException if email doesn't exist or password is wrong
     */
    public AuthResponse login(LoginRequest request) {
        logger.info("Login attempt for email: {}", request.getEmail());

        // Look up user by email.
        // We intentionally use the same error message for both "email not found"
        // and "wrong password" to prevent email enumeration attacks.
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Verify the provided password against the stored BCrypt hash.
        // BCrypt.matches() extracts the salt from the hash and re-encodes
        // the provided password for comparison.
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            logger.warn("Failed login attempt for email: {}", request.getEmail());
            throw new BadCredentialsException("Invalid email or password");
        }

        logger.info("User logged in successfully: {}", user.getEmail());

        // Generate JWT token for this session.
        String token = jwtService.generateToken(user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .user(UserDTO.fromEntity(user))
                .build();
    }
}
