package projiq.project.dto;

import projiq.project.entity.ProjectMember;

import java.util.UUID;

public class ProjectMemberDTO {

    private UUID userId;
    private String email;
    private String fullName;
    private String role;

    public ProjectMemberDTO() {
    }

    public ProjectMemberDTO(UUID userId, String email, String fullName, String role) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    public static ProjectMemberDTO from(ProjectMember pm) {
        return new ProjectMemberDTO(
                pm.getUser().getId(),
                pm.getUser().getEmail(),
                pm.getUser().getFullName(),
                pm.getRole()
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
