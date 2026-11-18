package projiq.organization.dto;

import projiq.organization.entity.Organization;

import java.time.Instant;
import java.util.UUID;

public class OrganizationDTO {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String roleName;
    private Instant createdAt;

    public OrganizationDTO() {
    }

    public OrganizationDTO(UUID id, String name, String slug, String description, String roleName, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.roleName = roleName;
        this.createdAt = createdAt;
    }

    public static OrganizationDTO from(Organization org) {
        return new OrganizationDTO(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getDescription(),
                null,
                org.getCreatedAt()
        );
    }

    public static OrganizationDTO from(Organization org, String roleName) {
        return new OrganizationDTO(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getDescription(),
                roleName,
                org.getCreatedAt()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getUserRole() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
