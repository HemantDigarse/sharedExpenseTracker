package com.spreetail.expenses.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>Extends {@link JpaRepository} which provides standard CRUD
 * operations (save, findById, findAll, delete, etc.) without
 * requiring manual implementation.
 *
 * <p>Custom query methods follow Spring Data naming conventions:
 * the method name is parsed into a JPA query automatically.
 * Example: {@code findByEmail(String email)} generates:
 * {@code SELECT u FROM User u WHERE u.email = :email}
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their email address.
     *
     * <p>Used by:
     * <ul>
     *   <li>Authentication: look up user during login</li>
     *   <li>Registration: check if email is already taken</li>
     *   <li>CSV Import: resolve member names to user IDs</li>
     * </ul>
     *
     * @param email the email address to search for (case-sensitive)
     * @return Optional containing the user if found, empty otherwise
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks if a user with the given email already exists.
     *
     * <p>More efficient than {@code findByEmail().isPresent()} because
     * it generates an EXISTS query instead of loading the full entity.
     * Used during registration to check for duplicate emails.
     *
     * @param email the email address to check
     * @return true if a user with this email exists
     */
    boolean existsByEmail(String email);

    /**
     * Finds a user by their full name (exact match).
     *
     * <p>Used by the CSV import pipeline to map CSV member names
     * (e.g., "Aisha", "Rohan") to user IDs. If no match is found,
     * ANOMALY_007 (unknown member) is raised.
     *
     * @param fullName the exact full name to search for
     * @return Optional containing the user if found, empty otherwise
     */
    Optional<User> findByFullName(String fullName);
}
