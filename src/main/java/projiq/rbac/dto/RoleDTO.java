package projiq.rbac.dto;

import projiq.rbac.entity.Permission;
import projiq.rbac.entity.Role;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RoleDTO {

    private UUID id;
    private String name;
    private String description;
    private boolean systemRole;
    private Set<String> permissions;

    public RoleDTO() {
    }

    public RoleDTO(UUID id, String name, String description, boolean systemRole, Set<String> permissions) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.systemRole = systemRole;
        this.permissions = permissions;
    }

    public static RoleDTO from(Role role) {
        Set<String> permNames = role.getPermissions() != null
                ? role.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet())
                : Set.of();

        return new RoleDTO(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.isSystemRole(),
                permNames
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public void setSystemRole(boolean systemRole) {
        this.systemRole = systemRole;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }
}
