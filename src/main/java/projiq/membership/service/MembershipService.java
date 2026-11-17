package projiq.membership.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projiq.auth.service.EmailService;
import projiq.auth.util.TokenHashUtils;
import projiq.common.exception.BadRequestException;
import projiq.common.exception.ConflictException;
import projiq.common.exception.ForbiddenException;
import projiq.common.exception.ResourceNotFoundException;
import projiq.membership.dto.*;
import projiq.membership.entity.InvitationStatus;
import projiq.membership.entity.OrganizationInvitation;
import projiq.membership.entity.OrganizationMember;
import projiq.membership.repository.OrganizationInvitationRepository;
import projiq.membership.repository.OrganizationMemberRepository;
import projiq.organization.entity.Organization;
import projiq.organization.repository.OrganizationRepository;
import projiq.rbac.entity.Role;
import projiq.rbac.repository.RoleRepository;
import projiq.rbac.service.RbacService;
import projiq.user.entity.User;
import projiq.user.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MembershipService {

    private final OrganizationMemberRepository memberRepository;
    private final OrganizationInvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RbacService rbacService;
    private final EmailService emailService;
    private final projiq.audit.service.AuditService auditService;
    private final projiq.notification.service.NotificationService notificationService;

    public MembershipService(OrganizationMemberRepository memberRepository,
                             OrganizationInvitationRepository invitationRepository,
                             OrganizationRepository organizationRepository,
                             RoleRepository roleRepository,
                             UserRepository userRepository,
                             RbacService rbacService,
                             EmailService emailService,
                             projiq.audit.service.AuditService auditService,
                             projiq.notification.service.NotificationService notificationService) {
        this.memberRepository = memberRepository;
        this.invitationRepository = invitationRepository;
        this.organizationRepository = organizationRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.rbacService = rbacService;
        this.emailService = emailService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<MemberDTO> getOrganizationMembers(UUID organizationId) {
        return memberRepository.findByOrganizationIdWithUserAndRole(organizationId).stream()
                .map(MemberDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public InvitationDTO inviteMember(UUID organizationId, InviteMemberRequest request, UUID inviterId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));

        User inviter = userRepository.findById(inviterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", inviterId));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", request.getRoleId()));

        if (!Objects.equals(role.getOrganization().getId(), organizationId)) {
            throw new BadRequestException("Role does not belong to this organization");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Check if user is already a member
        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            if (memberRepository.existsByOrganizationIdAndUserId(organizationId, user.getId())) {
                throw new ConflictException("User " + normalizedEmail + " is already a member of this organization");
            }
        });

        // Check for active pending invitation
        if (invitationRepository.existsByOrganizationIdAndEmailIgnoreCaseAndStatus(organizationId, normalizedEmail, InvitationStatus.PENDING)) {
            throw new ConflictException("An active invitation has already been sent to " + normalizedEmail);
        }

        // Privilege escalation prevention: only Owner can invite an Owner
        OrganizationMember inviterMember = memberRepository.findByOrganizationIdAndUserId(organizationId, inviterId)
                .orElseThrow(() -> new ForbiddenException("Inviter is not a member of this organization"));

        if ("Owner".equalsIgnoreCase(role.getName()) && !"Owner".equalsIgnoreCase(inviterMember.getRole().getName())) {
            throw new ForbiddenException("Only an organization Owner can invite members with the Owner role");
        }

        String rawToken = TokenHashUtils.generateSecureToken(32);
        String tokenHash = TokenHashUtils.hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(Duration.ofDays(7));

        OrganizationInvitation invitation = new OrganizationInvitation(organization, inviter, role, normalizedEmail, tokenHash, expiresAt);
        final OrganizationInvitation savedInvitation = invitationRepository.save(invitation);

        emailService.sendOrganizationInvitation(normalizedEmail, organization.getName(), inviter.getFullName(), rawToken);

        auditService.logAction(organizationId, inviterId, "MEMBER_INVITED", "MEMBER",
                savedInvitation.getId().toString(), "Invited " + normalizedEmail + " as " + role.getName(), null);

        // If invited user already has an account, notify them in-app
        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            notificationService.createNotification(
                    user.getId(),
                    organizationId,
                    "Organization Invitation",
                    "You have been invited to join " + organization.getName() + " as " + role.getName(),
                    "INVITATION_RECEIVED",
                    savedInvitation.getId().toString()
            );
        });

        return InvitationDTO.from(savedInvitation);
    }

    @Transactional(readOnly = true)
    public List<InvitationDTO> getPendingInvitations(UUID organizationId) {
        return invitationRepository.findByOrganizationIdAndStatus(organizationId, InvitationStatus.PENDING).stream()
                .filter(inv -> !inv.isExpired())
                .map(InvitationDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public MemberDTO acceptInvitation(AcceptInvitationRequest request, UUID userId) {
        String tokenHash = TokenHashUtils.hashToken(request.getToken().trim());

        OrganizationInvitation invitation = invitationRepository.findByTokenHashWithDetails(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid invitation token"));

        if (!invitation.isPending()) {
            throw new BadRequestException("Invitation is no longer valid or has expired");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Organization org = invitation.getOrganization();

        if (memberRepository.existsByOrganizationIdAndUserId(org.getId(), userId)) {
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitationRepository.save(invitation);
            throw new ConflictException("You are already a member of this organization");
        }

        OrganizationMember member = new OrganizationMember(org, user, invitation.getRole());
        member = memberRepository.save(member);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        rbacService.invalidateUserPermissions(org.getId(), userId);

        auditService.logAction(org.getId(), userId, "MEMBER_JOINED", "MEMBER",
                user.getId().toString(), user.getFullName() + " joined organization as " + invitation.getRole().getName(), null);

        return MemberDTO.from(member);
    }

    @Transactional
    public void rejectInvitation(AcceptInvitationRequest request) {
        String tokenHash = TokenHashUtils.hashToken(request.getToken().trim());
        OrganizationInvitation invitation = invitationRepository.findByTokenHashWithDetails(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid invitation token"));

        if (!invitation.isPending()) {
            throw new BadRequestException("Invitation is no longer valid or has expired");
        }

        invitation.setStatus(InvitationStatus.REJECTED);
        invitationRepository.save(invitation);
    }

    @Transactional
    public MemberDTO updateMemberRole(UUID organizationId, UUID targetUserId, UpdateMemberRoleRequest request, UUID actorId) {
        OrganizationMember targetMember = memberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "userId", targetUserId));

        OrganizationMember actorMember = memberRepository.findByOrganizationIdAndUserId(organizationId, actorId)
                .orElseThrow(() -> new ForbiddenException("Actor is not a member of this organization"));

        Role newRole = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", request.getRoleId()));

        if (!Objects.equals(newRole.getOrganization().getId(), organizationId)) {
            throw new BadRequestException("Role does not belong to this organization");
        }

        // Prevent self-role change
        if (targetUserId.equals(actorId)) {
            throw new ForbiddenException("You cannot modify your own role");
        }

        // Privilege escalation prevention
        boolean isActorOwner = "Owner".equalsIgnoreCase(actorMember.getRole().getName());
        boolean isTargetOwner = "Owner".equalsIgnoreCase(targetMember.getRole().getName());
        boolean isNewRoleOwner = "Owner".equalsIgnoreCase(newRole.getName());

        if (isTargetOwner && !isActorOwner) {
            throw new ForbiddenException("Only an Owner can modify another Owner's role");
        }

        if (isNewRoleOwner && !isActorOwner) {
            throw new ForbiddenException("Only an Owner can promote a member to Owner");
        }

        // Prevent removing/demoting the last Owner
        if (isTargetOwner && !isNewRoleOwner) {
            long ownerCount = memberRepository.countByOrganizationIdAndRoleName(organizationId, "Owner");
            if (ownerCount <= 1) {
                throw new BadRequestException("Cannot demote the last remaining Owner of the organization");
            }
        }

        targetMember.setRole(newRole);
        targetMember = memberRepository.save(targetMember);

        rbacService.invalidateUserPermissions(organizationId, targetUserId);

        auditService.logAction(organizationId, actorId, "MEMBER_ROLE_UPDATED", "MEMBER",
                targetUserId.toString(), "Updated role to " + newRole.getName() + " for user " + targetMember.getUser().getFullName(), null);

        notificationService.createNotification(
                targetUserId,
                organizationId,
                "Role Updated",
                "Your role in " + targetMember.getOrganization().getName() + " was changed to " + newRole.getName(),
                "ROLE_UPDATED",
                organizationId.toString()
        );

        return MemberDTO.from(targetMember);
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID targetUserId, UUID actorId) {
        OrganizationMember targetMember = memberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "userId", targetUserId));

        OrganizationMember actorMember = memberRepository.findByOrganizationIdAndUserId(organizationId, actorId)
                .orElseThrow(() -> new ForbiddenException("Actor is not a member of this organization"));

        boolean isActorOwner = "Owner".equalsIgnoreCase(actorMember.getRole().getName());
        boolean isTargetOwner = "Owner".equalsIgnoreCase(targetMember.getRole().getName());

        if (isTargetOwner && !isActorOwner) {
            throw new ForbiddenException("Only an Owner can remove another Owner");
        }

        // Prevent removing the last Owner
        if (isTargetOwner) {
            long ownerCount = memberRepository.countByOrganizationIdAndRoleName(organizationId, "Owner");
            if (ownerCount <= 1) {
                throw new BadRequestException("Cannot remove the last remaining Owner of the organization");
            }
        }

        memberRepository.delete(targetMember);
        rbacService.invalidateUserPermissions(organizationId, targetUserId);

        auditService.logAction(organizationId, actorId, "MEMBER_REMOVED", "MEMBER",
                targetUserId.toString(), "Removed user " + targetMember.getUser().getFullName() + " from organization", null);
    }
}
