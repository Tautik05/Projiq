package projiq.task.dto;

import projiq.task.entity.TaskComment;

import java.time.Instant;
import java.util.UUID;

public class TaskCommentDTO {

    private UUID id;
    private UUID taskId;
    private UUID userId;
    private String userName;
    private String content;
    private Instant createdAt;

    public TaskCommentDTO() {
    }

    public TaskCommentDTO(UUID id, UUID taskId, UUID userId, String userName, String content, Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.userId = userId;
        this.userName = userName;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static TaskCommentDTO from(TaskComment comment) {
        return new TaskCommentDTO(
                comment.getId(),
                comment.getTask().getId(),
                comment.getUser().getId(),
                comment.getUser().getFullName(),
                comment.getContent(),
                comment.getCreatedAt()
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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
