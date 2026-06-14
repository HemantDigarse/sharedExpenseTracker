package com.spreetail.expenses.group;

import com.spreetail.expenses.user.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Data Transfer Object for Group entity.
 *
 * <p>Includes the group's members with their membership date ranges.
 * This provides the full picture: who is in the group and when
 * they joined/left.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupDTO {

    private Long id;
    private String name;
    private String description;
    private UserDTO createdBy;
    private OffsetDateTime createdAt;

    /**
     * List of current and past members with their date ranges.
     * Populated when fetching group details (not in list view).
     */
    private List<MembershipDTO> members;

    /**
     * Nested DTO representing a member's participation in the group.
     * Includes user info and membership date range.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MembershipDTO {
        private Long membershipId;
        private UserDTO user;
        private LocalDate joinedAt;
        private LocalDate leftAt;

        /**
         * Whether this member is currently active (leftAt is null).
         */
        private boolean active;

        /**
         * Converts a GroupMembership entity to a MembershipDTO.
         */
        public static MembershipDTO fromEntity(GroupMembership membership) {
            return MembershipDTO.builder()
                    .membershipId(membership.getId())
                    .user(UserDTO.fromEntity(membership.getUser()))
                    .joinedAt(membership.getJoinedAt())
                    .leftAt(membership.getLeftAt())
                    .active(membership.isActive())
                    .build();
        }
    }

    /**
     * Converts a Group entity to a GroupDTO (without members).
     * Use {@link #fromEntityWithMembers} for the full version.
     */
    public static GroupDTO fromEntity(Group group) {
        return GroupDTO.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .createdBy(UserDTO.fromEntity(group.getCreatedBy()))
                .createdAt(group.getCreatedAt())
                .build();
    }

    /**
     * Converts a Group entity to a GroupDTO with member list.
     *
     * @param group       the group entity
     * @param memberships the group's memberships
     * @return GroupDTO with members populated
     */
    public static GroupDTO fromEntityWithMembers(Group group, List<GroupMembership> memberships) {
        GroupDTO dto = fromEntity(group);
        dto.setMembers(
                memberships.stream()
                        .map(MembershipDTO::fromEntity)
                        .toList()
        );
        return dto;
    }
}
