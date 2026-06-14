package com.spreetail.expenses.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data JPA repository for {@link GroupMembership} entities.
 *
 * <p>This repository contains the critical queries for time-ranged
 * membership lookups used by the balance calculation engine.
 */
@Repository
public interface GroupMembershipRepository extends JpaRepository<GroupMembership, Long> {

    /**
     * Finds ALL memberships (current and past) for a specific group.
     *
     * <p>Used by the group detail page to show all members with
     * their join/leave dates.
     *
     * @param groupId the group ID
     * @return all memberships for this group
     */
    List<GroupMembership> findByGroupId(Long groupId);

    /**
     * Finds currently ACTIVE memberships for a group.
     *
     * <p>Active = leftAt is null (member has not left).
     * Used to determine who should be included in new expenses.
     *
     * @param groupId the group ID
     * @return active memberships only
     */
    List<GroupMembership> findByGroupIdAndLeftAtIsNull(Long groupId);

    /**
     * Finds members who were active on a specific date.
     *
     * <p>THIS IS THE MOST IMPORTANT QUERY IN THE APPLICATION.
     * Used by BalanceCalculationService to determine which members
     * should be included in an expense split for a given date.
     *
     * <p>Logic: joinedAt <= date AND (leftAt IS NULL OR leftAt >= date)
     *
     * <p>Examples:
     * <ul>
     *   <li>Date: March 15 → returns Aisha, Rohan, Priya, Meera
     *       (Sam hasn't joined yet, Meera hasn't left yet)</li>
     *   <li>Date: April 20 → returns Aisha, Rohan, Priya, Sam
     *       (Meera left March 31, Sam joined April 15)</li>
     * </ul>
     *
     * @param groupId the group ID
     * @param date    the expense date to check membership against
     * @return memberships that were active on the given date
     */
    @Query("SELECT gm FROM GroupMembership gm " +
            "WHERE gm.group.id = :groupId " +
            "AND gm.joinedAt <= :date " +
            "AND (gm.leftAt IS NULL OR gm.leftAt >= :date)")
    List<GroupMembership> findActiveMembersOnDate(
            @Param("groupId") Long groupId,
            @Param("date") LocalDate date);

    /**
     * Finds all groups a user belongs to (current memberships only).
     *
     * <p>Used by the dashboard to show "My Groups" for the logged-in user.
     *
     * @param userId the user ID
     * @return active memberships for this user
     */
    List<GroupMembership> findByUserIdAndLeftAtIsNull(Long userId);

    /**
     * Finds all memberships for a user in all groups (including past).
     *
     * @param userId the user ID
     * @return all memberships for this user
     */
    List<GroupMembership> findByUserId(Long userId);

    /**
     * Checks if a user has an active membership in a group.
     *
     * <p>Used to validate operations like "add expense" — the payer
     * must be an active member of the group.
     *
     * @param groupId the group ID
     * @param userId  the user ID
     * @return true if the user is currently an active member
     */
    boolean existsByGroupIdAndUserIdAndLeftAtIsNull(Long groupId, Long userId);

    /**
     * Finds a specific membership by group and user (active only).
     *
     * <p>Used when updating membership (e.g., setting leftAt when
     * a member leaves the group).
     *
     * @param groupId the group ID
     * @param userId  the user ID
     * @return the active membership, or empty if not found
     */
    java.util.Optional<GroupMembership> findByGroupIdAndUserIdAndLeftAtIsNull(
            Long groupId, Long userId);
}
