package projiq.membership.dto;

import projiq.membership.entity.OrganizationMember;

import java.time.Instant;
import java.util.UUID;

public class MemberDTO {

    private UUID userId;
    private String email;
    private String fullName;
    private UUID roleId;
    private String roleName;
    private Instant joinedAt;

    public MemberDTO() {
    }

    public MemberDTO(UUID userId, String email, String fullName, UUID roleId, String roleName, Instant joinedAt) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.roleId = roleId;
        this.roleName = roleName;
        this.joinedAt = joinedAt;
    }

    public static MemberDTO from(OrganizationMember member) {
        return new MemberDTO(
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getFullName(),
                member.getRole().getId(),
                member.getRole().getName(),
                member.getJoinedAt()
        );
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }
}
