package projiq.membership.entity;

import jakarta.persistence.*;
import projiq.common.entity.BaseEntity;
import projiq.organization.entity.Organization;
import projiq.rbac.entity.Role;
import projiq.user.entity.User;

import java.time.Instant;

@Entity
@Table(name = "organization_members", uniqueConstraints = {
        @UniqueConstraint(name = "uq_org_member", columnNames = {"organization_id", "user_id"})
})
public class OrganizationMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    public OrganizationMember() {
        this.joinedAt = Instant.now();
    }

    public OrganizationMember(Organization organization, User user, Role role) {
        this.organization = organization;
        this.user = user;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }
}
