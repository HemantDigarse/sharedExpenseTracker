package com.spreetail.expenses.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Data Transfer Object for User entity.
 *
 * <p>CRITICAL: This DTO intentionally EXCLUDES {@code passwordHash}.
 * JPA entities are never exposed directly in API responses to prevent
 * leaking sensitive data and to decouple the API contract from the
 * database schema.
 *
 * <p>Used in:
 * <ul>
 *   <li>Login/register responses (with JWT token in AuthResponse)</li>
 *   <li>Group member lists</li>
 *   <li>Expense paid_by references</li>
 *   <li>Settlement paid_by/paid_to references</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private Long id;
    private String email;
    private String fullName;
    private OffsetDateTime createdAt;

    // ================================================================
    // MAPPER METHOD — Entity → DTO conversion
    // ================================================================
    // We use a static factory method instead of a separate Mapper class
    // because the mapping is simple (field-to-field, no transformation).
    // For complex mappings (e.g., nested objects), consider MapStruct.
    // ================================================================

    /**
     * Converts a User entity to a UserDTO.
     *
     * <p>This is the ONLY way to create a UserDTO from a User entity.
     * It ensures passwordHash is never accidentally included.
     *
     * @param user the JPA entity (must not be null)
     * @return a UserDTO with all safe fields populated
     */
    public static UserDTO fromEntity(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
