package projiq.project.dto;

import jakarta.validation.constraints.Size;
import projiq.project.entity.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

public class UpdateProjectRequest {

    @Size(min = 2, max = 150, message = "Project name must be between 2 and 150 characters")
    private String name;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    private ProjectStatus status;

    private UUID managerId;

    private Instant dueDate;

    public UpdateProjectRequest() {
    }

    public UpdateProjectRequest(String name, String description, ProjectStatus status, UUID managerId, Instant dueDate) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.managerId = managerId;
        this.dueDate = dueDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public void setManagerId(UUID managerId) {
        this.managerId = managerId;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }
}
