package com.spreetail.expenses.group;

import com.spreetail.expenses.common.BusinessRuleException;
import com.spreetail.expenses.common.ResourceNotFoundException;
import com.spreetail.expenses.user.User;
import com.spreetail.expenses.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of {@link GroupService}.
 *
 * <p>Handles group creation, member addition/removal, and group queries.
 * All write operations are wrapped in @Transactional to ensure atomicity.
 *
 * <p>Key business rules enforced:
 * <ul>
 *   <li>Creator is automatically added as the first member on group creation</li>
 *   <li>A user cannot be added to a group they're already active in</li>
 *   <li>Removing a member sets leftAt — the record is preserved for
 *       historical balance calculations</li>
 *   <li>leftAt must be on or after joinedAt (enforced by DB constraint)</li>
 * </ul>
 */
@Service
public class GroupServiceImpl implements GroupService {

    private static final Logger logger = LoggerFactory.getLogger(GroupServiceImpl.class);

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public GroupServiceImpl(GroupRepository groupRepository,
                            GroupMembershipRepository membershipRepository,
                            UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a new expense group and adds the creator as the first member.
     *
     * <p>@Transactional ensures both the group creation and the membership
     * creation succeed or fail together. If adding the membership fails
     * (e.g., user not found), the group creation is also rolled back.
     *
     * @param name        group name
     * @param description optional description
     * @param creatorId   the ID of the user creating the group
     * @return the created group DTO with the creator as the first member
     */
    @Override
    @Transactional
    public GroupDTO createGroup(String name, String description, Long creatorId) {
        logger.info("Creating group '{}' by user ID: {}", name, creatorId);

        // Look up the creator user — must exist
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", creatorId));

        // Create the group entity
        Group group = Group.builder()
                .name(name)
                .description(description)
                .createdBy(creator)
                .build();

        Group savedGroup = groupRepository.save(group);

        // Automatically add the creator as the first member.
        // The join date defaults to today — can be overridden via addMember.
        GroupMembership creatorMembership = GroupMembership.builder()
                .group(savedGroup)
                .user(creator)
                .joinedAt(LocalDate.now())
                .build();

        membershipRepository.save(creatorMembership);

        logger.info("Group '{}' created successfully (ID: {})", name, savedGroup.getId());

        // Return the group with the creator membership
        List<GroupMembership> memberships = membershipRepository.findByGroupId(savedGroup.getId());
        return GroupDTO.fromEntityWithMembers(savedGroup, memberships);
    }

    /**
     * Retrieves a group by ID with all its members (current and past).
     *
     * @param groupId the group ID
     * @return the group DTO with full member list
     * @throws ResourceNotFoundException if the group doesn't exist
     */
    @Override
    @Transactional(readOnly = true)
    public GroupDTO getGroupById(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        List<GroupMembership> memberships = membershipRepository.findByGroupId(groupId);
        return GroupDTO.fromEntityWithMembers(group, memberships);
    }

    /**
     * Retrieves all groups.
     */
    @Override
    @Transactional(readOnly = true)
    public List<GroupDTO> getAllGroups() {
        return groupRepository.findAll()
                .stream()
                .map(GroupDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves groups where the user is currently an active member.
     *
     * @param userId the user ID
     * @return groups the user currently belongs to
     */
    @Override
    @Transactional(readOnly = true)
    public List<GroupDTO> getGroupsByUserId(Long userId) {
        return membershipRepository.findByUserIdAndLeftAtIsNull(userId)
                .stream()
                .map(membership -> GroupDTO.fromEntity(membership.getGroup()))
                .collect(Collectors.toList());
    }

    /**
     * Adds a member to a group with a specific join date.
     *
     * <p>Business rules:
     * <ul>
     *   <li>The user must exist</li>
     *   <li>The group must exist</li>
     *   <li>The user must not already be an active member of this group</li>
     * </ul>
     *
     * <p>A user CAN be re-added after leaving (different joinedAt dates),
     * but cannot have overlapping active memberships.
     *
     * @param groupId  the group ID
     * @param userId   the user ID to add
     * @param joinedAt when the member joined
     * @return the updated group DTO with all members
     */
    @Override
    @Transactional
    public GroupDTO addMember(Long groupId, Long userId, LocalDate joinedAt) {
        logger.info("Adding user {} to group {} (joined: {})", userId, groupId, joinedAt);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Check if user is already an active member of this group.
        // Prevents duplicate memberships.
        if (membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId)) {
            throw new BusinessRuleException(
                    "User '" + user.getFullName() + "' is already an active member of this group"
            );
        }

        GroupMembership membership = GroupMembership.builder()
                .group(group)
                .user(user)
                .joinedAt(joinedAt)
                .build();

        membershipRepository.save(membership);
        logger.info("User {} added to group {} successfully", userId, groupId);

        List<GroupMembership> memberships = membershipRepository.findByGroupId(groupId);
        return GroupDTO.fromEntityWithMembers(group, memberships);
    }

    /**
     * Removes a member from a group by setting their leftAt date.
     *
     * <p>IMPORTANT: This does NOT delete the membership record.
     * The record is preserved with leftAt set, so that historical
     * balance calculations remain accurate.
     *
     * <p>Example: Meera left on March 31. Her membership record shows:
     * joinedAt=Feb 1, leftAt=March 31. February and March expenses
     * still include Meera in their splits.
     *
     * @param groupId the group ID
     * @param userId  the user ID to remove
     * @param leftAt  the date the member left
     * @return the updated group DTO
     */
    @Override
    @Transactional
    public GroupDTO removeMember(Long groupId, Long userId, LocalDate leftAt) {
        logger.info("Removing user {} from group {} (left: {})", userId, groupId, leftAt);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        // Find the active membership for this user in this group.
        GroupMembership membership = membershipRepository
                .findByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId)
                .orElseThrow(() -> new BusinessRuleException(
                        "User is not an active member of this group"
                ));

        // Validate: leftAt must be on or after joinedAt.
        // The DB constraint also enforces this, but we check here
        // for a better error message.
        if (leftAt.isBefore(membership.getJoinedAt())) {
            throw new BusinessRuleException(
                    "Leave date (" + leftAt + ") cannot be before join date (" +
                            membership.getJoinedAt() + ")"
            );
        }

        // Set leftAt — do NOT delete the record.
        membership.setLeftAt(leftAt);
        membershipRepository.save(membership);

        logger.info("User {} removed from group {} (left: {})", userId, groupId, leftAt);

        List<GroupMembership> memberships = membershipRepository.findByGroupId(groupId);
        return GroupDTO.fromEntityWithMembers(group, memberships);
    }
}
