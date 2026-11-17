package projiq.membership.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class UpdateMemberRoleRequest {

    @NotNull(message = "New role ID is required")
    private UUID roleId;

    public UpdateMemberRoleRequest() {
    }

    public UpdateMemberRoleRequest(UUID roleId) {
        this.roleId = roleId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }
}
