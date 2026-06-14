package com.spreetail.expenses.group;

import java.time.LocalDate;
import java.util.List;

/**
 * Service interface for group and membership operations.
 *
 * <p>Abstracted as an interface for testability (architecture rule #7).
 *
 * @see GroupServiceImpl
 */
public interface GroupService {

    /**
     * Creates a new expense group.
     *
     * @param name        the group name
     * @param description optional description
     * @param creatorId   the ID of the user creating the group
     * @return the created group DTO
     */
    GroupDTO createGroup(String name, String description, Long creatorId);

    /**
     * Retrieves a group by ID with its members.
     *
     * @param groupId the group ID
     * @return the group DTO with member list
     */
    GroupDTO getGroupById(Long groupId);

    /**
     * Retrieves all groups (for admin/listing purposes).
     *
     * @return list of all group DTOs
     */
    List<GroupDTO> getAllGroups();

    /**
     * Retrieves groups where the given user is currently an active member.
     *
     * @param userId the user ID
     * @return list of group DTOs the user belongs to
     */
    List<GroupDTO> getGroupsByUserId(Long userId);

    /**
     * Adds a member to a group with a specific join date.
     *
     * @param groupId  the group ID
     * @param userId   the user ID
     * @param joinedAt the membership start date
     * @return the updated group DTO with members
     */
    GroupDTO addMember(Long groupId, Long userId, LocalDate joinedAt);

    /**
     * Removes a member from a group by setting their leftAt date.
     *
     * <p>Does NOT delete the membership record — sets leftAt to
     * preserve historical membership data for balance calculations.
     *
     * @param groupId the group ID
     * @param userId  the user ID
     * @param leftAt  the date the member left
     * @return the updated group DTO
     */
    GroupDTO removeMember(Long groupId, Long userId, LocalDate leftAt);
}
