package projiq.membership.dto;

import projiq.membership.entity.InvitationStatus;
import projiq.membership.entity.OrganizationInvitation;

import java.time.Instant;
import java.util.UUID;

public class InvitationDTO {

    private UUID id;
    private UUID organizationId;
    private String organizationName;
    private String email;
    private UUID roleId;
    private String roleName;
    private String inviterName;
    private InvitationStatus status;
    private Instant expiresAt;
    private Instant createdAt;

    public InvitationDTO() {
    }

    public InvitationDTO(UUID id, UUID organizationId, String organizationName, String email, UUID roleId,
                         String roleName, String inviterName, InvitationStatus status, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.organizationName = organizationName;
        this.email = email;
        this.roleId = roleId;
        this.roleName = roleName;
        this.inviterName = inviterName;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static InvitationDTO from(OrganizationInvitation inv) {
        return new InvitationDTO(
                inv.getId(),
                inv.getOrganization().getId(),
                inv.getOrganization().getName(),
                inv.getEmail(),
                inv.getRole().getId(),
                inv.getRole().getName(),
                inv.getInviter().getFullName(),
                inv.getStatus(),
                inv.getExpiresAt(),
                inv.getCreatedAt()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getInviterName() {
        return inviterName;
    }

    public void setInviterName(String inviterName) {
        this.inviterName = inviterName;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public void setStatus(InvitationStatus status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
