package projiq.task.dto;

import projiq.task.entity.Task;
import projiq.task.entity.TaskPriority;
import projiq.task.entity.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public class TaskDTO {

    private UUID id;
    private UUID organizationId;
    private UUID projectId;
    private String projectTitle;
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private Instant dueDate;
    private UUID assigneeId;
    private String assigneeName;
    private String createdByName;
    private Instant createdAt;
    private Instant updatedAt;

    public TaskDTO() {
    }

    public TaskDTO(UUID id, UUID organizationId, UUID projectId, String projectTitle, String title, String description,
                   TaskStatus status, TaskPriority priority, Instant dueDate, UUID assigneeId, String assigneeName,
                   String createdByName, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.dueDate = dueDate;
        this.assigneeId = assigneeId;
        this.assigneeName = assigneeName;
        this.createdByName = createdByName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static TaskDTO from(Task task) {
        UUID aId = task.getAssignee() != null ? task.getAssignee().getId() : null;
        String aName = task.getAssignee() != null ? task.getAssignee().getFullName() : null;
        String cName = task.getCreatedBy() != null ? task.getCreatedBy().getFullName() : null;
        String pName = task.getProject() != null ? task.getProject().getName() : null;

        return new TaskDTO(
                task.getId(),
                task.getOrganization().getId(),
                task.getProject().getId(),
                pName,
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                aId,
                aName,
                cName,
                task.getCreatedAt(),
                task.getUpdatedAt()
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

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
    }

    public String getAssigneeName() {
        return assigneeName;
    }

    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
