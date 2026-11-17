package projiq.audit.dto;

import projiq.audit.entity.AuditLog;

import java.time.Instant;
import java.util.UUID;

public class AuditLogDTO {

    private UUID id;
    private UUID organizationId;
    private UUID actorId;
    private String actorName;
    private String action;
    private String resourceType;
    private String resourceId;
    private String details;
    private String ipAddress;
    private Instant createdAt;

    public AuditLogDTO() {
    }

    public AuditLogDTO(UUID id, UUID organizationId, UUID actorId, String actorName, String action,
                       String resourceType, String resourceId, String details, String ipAddress, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.actorId = actorId;
        this.actorName = actorName;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.details = details;
        this.ipAddress = ipAddress;
        this.createdAt = createdAt;
    }

    public static AuditLogDTO from(AuditLog log) {
        UUID aId = log.getActor() != null ? log.getActor().getId() : null;
        String aName = log.getActor() != null ? log.getActor().getFullName() : "System";

        return new AuditLogDTO(
                log.getId(),
                log.getOrganization().getId(),
                aId,
                aName,
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getDetails(),
                log.getIpAddress(),
                log.getCreatedAt()
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

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
