package projiq.task.dto;

import projiq.task.entity.TaskActivity;

import java.time.Instant;
import java.util.UUID;

public class TaskActivityDTO {

    private UUID id;
    private UUID taskId;
    private UUID actorId;
    private String actorName;
    private String action;
    private String details;
    private Instant createdAt;

    public TaskActivityDTO() {
    }

    public TaskActivityDTO(UUID id, UUID taskId, UUID actorId, String actorName, String action, String details, Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.actorId = actorId;
        this.actorName = actorName;
        this.action = action;
        this.details = details;
        this.createdAt = createdAt;
    }

    public static TaskActivityDTO from(TaskActivity activity) {
        return new TaskActivityDTO(
                activity.getId(),
                activity.getTask().getId(),
                activity.getActor().getId(),
                activity.getActor().getFullName(),
                activity.getAction(),
                activity.getDetails(),
                activity.getCreatedAt()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
