package com.spreetail.expenses.user;

import com.spreetail.expenses.common.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of {@link UserService} providing user-related
 * business operations.
 *
 * <p>This service is a thin layer over {@link UserRepository} that:
 * <ul>
 *   <li>Converts entities to DTOs (never expose JPA entities)</li>
 *   <li>Throws typed exceptions for error handling</li>
 *   <li>Adds logging for debugging and audit</li>
 * </ul>
 *
 * <p>Read operations use {@code @Transactional(readOnly = true)}
 * to hint Hibernate that no writes will occur, enabling optimizations
 * like skipping dirty checking.
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;

    /**
     * Constructor injection (preferred over @Autowired field injection).
     * Makes dependencies explicit and enables constructor-based testing.
     */
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Retrieves a user by their ID.
     *
     * @param id the user ID to look up
     * @return UserDTO with the user's safe fields
     * @throws ResourceNotFoundException if no user exists with this ID
     */
    @Override
    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        logger.debug("Looking up user by ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return UserDTO.fromEntity(user);
    }

    /**
     * Retrieves a user by their email address.
     *
     * @param email the email to look up
     * @return UserDTO with the user's safe fields
     * @throws ResourceNotFoundException if no user exists with this email
     */
    @Override
    @Transactional(readOnly = true)
    public UserDTO getUserByEmail(String email) {
        logger.debug("Looking up user by email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return UserDTO.fromEntity(user);
    }

    /**
     * Retrieves all registered users.
     *
     * <p>Used by the group membership management UI to show
     * available users for adding to a group.
     *
     * @return list of all user DTOs
     */
    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        logger.debug("Retrieving all users");
        return userRepository.findAll()
                .stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
