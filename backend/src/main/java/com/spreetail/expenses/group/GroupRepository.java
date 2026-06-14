package com.spreetail.expenses.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Group} entities.
 *
 * <p>Provides standard CRUD operations for expense groups.
 * Custom queries (e.g., "groups where user is a member") are
 * handled through {@link GroupMembershipRepository} joins.
 */
@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {
}
