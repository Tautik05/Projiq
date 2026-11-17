package projiq.membership.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projiq.common.response.ApiResponse;
import projiq.common.security.SecurityUtils;
import projiq.membership.dto.*;
import projiq.membership.service.MembershipService;
import projiq.rbac.annotation.RequirePermission;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Memberships and Invitations", description = "Endpoints for managing workspace members, roles, and invitations")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping("/organizations/{orgId}/members")
    @RequirePermission("organization.read")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "List all members of an organization")
    public ResponseEntity<ApiResponse<List<MemberDTO>>> getMembers(@PathVariable UUID orgId) {
        List<MemberDTO> members = membershipService.getOrganizationMembers(orgId);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @PostMapping("/organizations/{orgId}/invitations")
    @RequirePermission("member.invite")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Invite a new member to the organization")
    public ResponseEntity<ApiResponse<InvitationDTO>> inviteMember(
            @PathVariable UUID orgId,
            @Valid @RequestBody InviteMemberRequest request) {
        UUID inviterId = SecurityUtils.getCurrentUserId();
        InvitationDTO invitation = membershipService.inviteMember(orgId, request, inviterId);
        return new ResponseEntity<>(ApiResponse.success("Invitation sent successfully", invitation), HttpStatus.CREATED);
    }

    @GetMapping("/organizations/{orgId}/invitations")
    @RequirePermission("member.invite")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "List pending invitations for the organization")
    public ResponseEntity<ApiResponse<List<InvitationDTO>>> getPendingInvitations(@PathVariable UUID orgId) {
        List<InvitationDTO> invitations = membershipService.getPendingInvitations(orgId);
        return ResponseEntity.ok(ApiResponse.success(invitations));
    }

    @PostMapping("/invitations/accept")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Accept an organization invitation")
    public ResponseEntity<ApiResponse<MemberDTO>> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        MemberDTO member = membershipService.acceptInvitation(request, userId);
        return ResponseEntity.ok(ApiResponse.success("Invitation accepted successfully", member));
    }

    @PostMapping("/invitations/reject")
    @Operation(summary = "Reject an organization invitation")
    public ResponseEntity<ApiResponse<Void>> rejectInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        membershipService.rejectInvitation(request);
        return ResponseEntity.ok(ApiResponse.success("Invitation rejected", null));
    }

    @PatchMapping("/organizations/{orgId}/members/{userId}/role")
    @RequirePermission("member.update_role")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Update a member's role within the organization")
    public ResponseEntity<ApiResponse<MemberDTO>> updateMemberRole(
            @PathVariable UUID orgId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        UUID actorId = SecurityUtils.getCurrentUserId();
        MemberDTO member = membershipService.updateMemberRole(orgId, userId, request, actorId);
        return ResponseEntity.ok(ApiResponse.success("Member role updated successfully", member));
    }

    @DeleteMapping("/organizations/{orgId}/members/{userId}")
    @RequirePermission("member.remove")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Remove a member from the organization")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable UUID orgId,
            @PathVariable UUID userId) {
        UUID actorId = SecurityUtils.getCurrentUserId();
        membershipService.removeMember(orgId, userId, actorId);
        return ResponseEntity.ok(ApiResponse.success("Member removed from organization", null));
    }
}
