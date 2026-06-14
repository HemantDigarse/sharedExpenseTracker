package com.spreetail.expenses.group;

import com.spreetail.expenses.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for group and membership management endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST   /api/groups          — create a new group</li>
 *   <li>GET    /api/groups          — list all groups</li>
 *   <li>GET    /api/groups/{id}     — get group details with members</li>
 *   <li>POST   /api/groups/{id}/members     — add a member</li>
 *   <li>PUT    /api/groups/{id}/members/{userId}/leave — remove a member</li>
 * </ul>
 *
 * <p>All endpoints require JWT authentication (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final com.spreetail.expenses.user.UserRepository userRepository;

    public GroupController(GroupService groupService,
                           com.spreetail.expenses.user.UserRepository userRepository) {
        this.groupService = groupService;
        this.userRepository = userRepository;
    }

    // ================================================================
    // REQUEST DTOs
    // ================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CreateGroupRequest {
        @NotBlank(message = "Group name is required")
        private String name;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AddMemberRequest {
        @NotNull(message = "User ID is required")
        private Long userId;
        @NotNull(message = "Join date is required")
        private LocalDate joinedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class RemoveMemberRequest {
        @NotNull(message = "Leave date is required")
        private LocalDate leftAt;
    }

    // ================================================================
    // ENDPOINTS
    // ================================================================

    /**
     * Creates a new expense group.
     * The authenticated user is automatically added as the first member.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<GroupDTO>> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Resolve the authenticated user's ID from their email
        Long userId = resolveUserId(userDetails);

        GroupDTO group = groupService.createGroup(
                request.getName(), request.getDescription(), userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(group, "Group created successfully"));
    }

    /**
     * Lists all groups.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<GroupDTO>>> getAllGroups() {
        List<GroupDTO> groups = groupService.getAllGroups();
        return ResponseEntity.ok(
                ApiResponse.success(groups, "Groups retrieved successfully")
        );
    }

    /**
     * Gets a specific group with its member list.
     */
    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<GroupDTO>> getGroup(@PathVariable Long groupId) {
        GroupDTO group = groupService.getGroupById(groupId);
        return ResponseEntity.ok(
                ApiResponse.success(group, "Group retrieved successfully")
        );
    }

    /**
     * Gets groups the authenticated user belongs to.
     */
    @GetMapping("/my-groups")
    public ResponseEntity<ApiResponse<List<GroupDTO>>> getMyGroups(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = resolveUserId(userDetails);
        List<GroupDTO> groups = groupService.getGroupsByUserId(userId);
        return ResponseEntity.ok(
                ApiResponse.success(groups, "Your groups retrieved successfully")
        );
    }

    /**
     * Adds a member to a group with a specific join date.
     */
    @PostMapping("/{groupId}/members")
    public ResponseEntity<ApiResponse<GroupDTO>> addMember(
            @PathVariable Long groupId,
            @Valid @RequestBody AddMemberRequest request) {

        GroupDTO group = groupService.addMember(
                groupId, request.getUserId(), request.getJoinedAt());

        return ResponseEntity.ok(
                ApiResponse.success(group, "Member added successfully")
        );
    }

    /**
     * Removes a member from a group by setting their leave date.
     * Does NOT delete the membership — preserves history for balance calculations.
     */
    @PutMapping("/{groupId}/members/{userId}/leave")
    public ResponseEntity<ApiResponse<GroupDTO>> removeMember(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @Valid @RequestBody RemoveMemberRequest request) {

        GroupDTO group = groupService.removeMember(groupId, userId, request.getLeftAt());

        return ResponseEntity.ok(
                ApiResponse.success(group, "Member removed successfully")
        );
    }

    // ================================================================
    // HELPERS
    // ================================================================

    /**
     * Resolves the authenticated user's database ID from their email.
     *
     * <p>Spring Security's UserDetails contains the email (set during JWT auth).
     * We need the database ID for service layer calls.
     */
    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new com.spreetail.expenses.common.ResourceNotFoundException(
                        "User", "email", userDetails.getUsername()))
                .getId();
    }
}
