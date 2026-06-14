package com.spreetail.expenses.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

/**
 * Service responsible for JWT token generation, parsing, and validation.
 *
 * <p>DESIGN DECISION (see DECISIONS.md: "JWT vs session auth"):
 * We use stateless JWT tokens instead of server-side sessions because:
 * <ol>
 *   <li>Stateless: no session store needed, scales horizontally</li>
 *   <li>Frontend can include token in Authorization header</li>
 *   <li>Works well with React SPA architecture</li>
 *   <li>Token contains user identity — no DB lookup per request</li>
 * </ol>
 *
 * <p>Token structure:
 * <ul>
 *   <li>Subject: user email (unique identifier)</li>
 *   <li>Issued at: creation timestamp</li>
 *   <li>Expiration: configurable (default 24 hours)</li>
 *   <li>Algorithm: HS256 (HMAC-SHA256)</li>
 * </ul>
 *
 * <p>Security considerations:
 * <ul>
 *   <li>Secret key must be at least 256 bits (32 characters)</li>
 *   <li>Tokens are NOT encrypted — don't put sensitive data in claims</li>
 *   <li>Expired tokens are rejected (checked in validateToken)</li>
 * </ul>
 */
@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    /**
     * Secret key for signing tokens.
     * Injected from application.properties: jwt.secret
     * MUST be changed from default in production.
     */
    private final String jwtSecret;

    /**
     * Token validity period in milliseconds.
     * Default: 86400000 (24 hours).
     * Injected from application.properties: jwt.expiration
     */
    private final long jwtExpiration;

    public JwtService(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration}") long jwtExpiration) {
        this.jwtSecret = jwtSecret;
        this.jwtExpiration = jwtExpiration;
    }

    /**
     * Generates a JWT token for a given user email.
     *
     * <p>The token contains:
     * <ul>
     *   <li>Subject: the user's email (used for identification)</li>
     *   <li>IssuedAt: current timestamp</li>
     *   <li>Expiration: current time + configured expiration period</li>
     * </ul>
     *
     * @param email the user's email to encode in the token
     * @return a signed JWT token string
     */
    public String generateToken(String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        String token = Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();

        logger.debug("Generated JWT token for user: {}", email);
        return token;
    }

    /**
     * Extracts the user email (subject) from a JWT token.
     *
     * @param token the JWT token string
     * @return the email encoded in the token's subject claim
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the expiration date from a JWT token.
     *
     * @param token the JWT token string
     * @return the token's expiration date
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Generic claim extractor using a function reference.
     *
     * <p>This pattern avoids duplicating the token parsing logic
     * for each claim type. Usage:
     * <pre>
     * String email = extractClaim(token, Claims::getSubject);
     * Date expiry = extractClaim(token, Claims::getExpiration);
     * </pre>
     *
     * @param token          the JWT token string
     * @param claimsResolver function that extracts the desired claim
     * @param <T>            the return type of the claim
     * @return the extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Validates a JWT token against a given email.
     *
     * <p>A token is valid if:
     * <ol>
     *   <li>The token's subject (email) matches the provided email</li>
     *   <li>The token has not expired</li>
     *   <li>The token's signature is valid (not tampered with)</li>
     * </ol>
     *
     * @param token the JWT token to validate
     * @param email the email to validate against
     * @return true if the token is valid for this email
     */
    public boolean validateToken(String token, String email) {
        try {
            final String tokenEmail = extractEmail(token);
            boolean isValid = tokenEmail.equals(email) && !isTokenExpired(token);
            if (!isValid) {
                logger.debug("Token validation failed for email: {}", email);
            }
            return isValid;
        } catch (ExpiredJwtException e) {
            logger.debug("JWT token expired: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            logger.debug("Malformed JWT token: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            logger.debug("Unsupported JWT token: {}", e.getMessage());
            return false;
        } catch (SignatureException e) {
            logger.debug("Invalid JWT signature: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            logger.debug("JWT claims string is empty: {}", e.getMessage());
            return false;
        }
    }

    // ================================================================
    // PRIVATE HELPER METHODS
    // ================================================================

    /**
     * Checks if a token has expired by comparing its expiration
     * date to the current time.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Parses a JWT token and extracts all claims.
     *
     * <p>This method verifies the token's signature using the
     * signing key. If the signature is invalid, an exception is thrown.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Creates the signing key from the configured secret.
     *
     * <p>The secret is Base64-decoded and used to create an
     * HMAC-SHA key. For HS256, the key must be at least 256 bits.
     *
     * <p>If the secret is not Base64-encoded (like a plain text
     * development key), we use it directly as bytes.
     */
    private SecretKey getSigningKey() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (DecodingException | IllegalArgumentException e) {
            // Fallback: use the secret as raw bytes (for dev/testing)
            // In production, always use a proper Base64-encoded secret
            logger.warn("JWT secret is not Base64-encoded, using raw bytes. " +
                    "Set a proper Base64-encoded secret in production.");
            return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        }
    }
}
