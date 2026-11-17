package projiq.project.entity;

import jakarta.persistence.*;
import projiq.common.entity.BaseEntity;
import projiq.user.entity.User;

@Entity
@Table(name = "project_members", uniqueConstraints = {
        @UniqueConstraint(name = "uq_project_member", columnNames = {"project_id", "user_id"})
})
public class ProjectMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "role", length = 50)
    private String role = "CONTRIBUTOR";

    public ProjectMember() {
    }

    public ProjectMember(Project project, User user, String role) {
        this.project = project;
        this.user = user;
        this.role = role != null ? role : "CONTRIBUTOR";
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
