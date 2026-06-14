package com.spreetail.expenses.user;

import java.util.List;

/**
 * Service interface for user-related business operations.
 *
 * <p>Abstracted as an interface following the architecture rule:
 * "Every Service class must have a corresponding interface for testability."
 *
 * <p>This allows unit tests to mock the service without depending on
 * the database, Spring Security, or other infrastructure.
 *
 * @see UserServiceImpl
 */
public interface UserService {

    /**
     * Retrieves a user by their ID.
     *
     * @param id the user ID
     * @return the user DTO
     * @throws com.spreetail.expenses.common.ResourceNotFoundException if not found
     */
    UserDTO getUserById(Long id);

    /**
     * Retrieves a user by their email address.
     *
     * @param email the email address
     * @return the user DTO
     * @throws com.spreetail.expenses.common.ResourceNotFoundException if not found
     */
    UserDTO getUserByEmail(String email);

    /**
     * Retrieves all registered users.
     *
     * @return list of all user DTOs
     */
    List<UserDTO> getAllUsers();
}
