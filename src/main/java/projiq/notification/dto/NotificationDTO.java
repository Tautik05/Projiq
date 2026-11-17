package projiq.notification.dto;

import projiq.notification.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public class NotificationDTO {

    private UUID id;
    private UUID organizationId;
    private String title;
    private String message;
    private String type;
    private String resourceId;
    private boolean read;
    private Instant readAt;
    private Instant createdAt;

    public NotificationDTO() {
    }

    public NotificationDTO(UUID id, UUID organizationId, String title, String message, String type,
                           String resourceId, boolean read, Instant readAt, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.resourceId = resourceId;
        this.read = read;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }

    public static NotificationDTO from(Notification n) {
        UUID orgId = n.getOrganization() != null ? n.getOrganization().getId() : null;
        return new NotificationDTO(
                n.getId(),
                orgId,
                n.getTitle(),
                n.getMessage(),
                n.getType(),
                n.getResourceId(),
                n.isRead(),
                n.getReadAt(),
                n.getCreatedAt()
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
