package projiq.project.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class AddProjectMemberRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    private String role;

    public AddProjectMemberRequest() {
    }

    public AddProjectMemberRequest(UUID userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
