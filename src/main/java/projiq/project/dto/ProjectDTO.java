package projiq.project.dto;

import projiq.project.entity.Project;
import projiq.project.entity.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

public class ProjectDTO {

    private UUID id;
    private UUID organizationId;
    private String name;
    private String description;
    private ProjectStatus status;
    private UUID managerId;
    private String managerName;
    private Instant dueDate;
    private String createdByName;
    private Instant createdAt;

    public ProjectDTO() {
    }

    public ProjectDTO(UUID id, UUID organizationId, String name, String description, ProjectStatus status,
                      UUID managerId, String managerName, Instant dueDate, String createdByName, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.managerId = managerId;
        this.managerName = managerName;
        this.dueDate = dueDate;
        this.createdByName = createdByName;
        this.createdAt = createdAt;
    }

    public static ProjectDTO from(Project project) {
        UUID mgrId = project.getManager() != null ? project.getManager().getId() : null;
        String mgrName = project.getManager() != null ? project.getManager().getFullName() : null;
        String creatorName = project.getCreatedBy() != null ? project.getCreatedBy().getFullName() : null;

        return new ProjectDTO(
                project.getId(),
                project.getOrganization().getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                mgrId,
                mgrName,
                project.getDueDate(),
                creatorName,
                project.getCreatedAt()
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

    public String getManagerName() {
        return managerName;
    }

    public void setManagerName(String managerName) {
        this.managerName = managerName;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
