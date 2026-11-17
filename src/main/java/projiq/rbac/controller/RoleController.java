package projiq.rbac.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projiq.common.exception.ResourceNotFoundException;
import projiq.common.response.ApiResponse;
import projiq.organization.entity.Organization;
import projiq.organization.repository.OrganizationRepository;
import projiq.rbac.annotation.RequirePermission;
import projiq.rbac.dto.CreateRoleRequest;
import projiq.rbac.dto.RoleDTO;
import projiq.rbac.dto.UpdateRoleRequest;
import projiq.rbac.service.RbacService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Roles and Permissions", description = "Endpoints for managing custom roles and permissions")
@SecurityRequirement(name = "BearerAuth")
public class RoleController {

    private final RbacService rbacService;
    private final OrganizationRepository organizationRepository;

    public RoleController(RbacService rbacService, OrganizationRepository organizationRepository) {
        this.rbacService = rbacService;
        this.organizationRepository = organizationRepository;
    }

    @GetMapping("/organizations/{orgId}/roles")
    @RequirePermission("organization.read")
    @Operation(summary = "List all roles defined for an organization")
    public ResponseEntity<ApiResponse<List<RoleDTO>>> getRoles(@PathVariable UUID orgId) {
        List<RoleDTO> roles = rbacService.getOrganizationRoles(orgId);
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    @PostMapping("/organizations/{orgId}/roles")
    @RequirePermission("role.manage")
    @Operation(summary = "Create a custom role for an organization")
    public ResponseEntity<ApiResponse<RoleDTO>> createRole(
            @PathVariable UUID orgId,
            @Valid @RequestBody CreateRoleRequest request) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", orgId));

        RoleDTO role = rbacService.createCustomRole(orgId, request, org);
        return new ResponseEntity<>(ApiResponse.success("Role created successfully", role), HttpStatus.CREATED);
    }

    @PatchMapping("/organizations/{orgId}/roles/{roleId}")
    @RequirePermission("role.manage")
    @Operation(summary = "Update role name, description, or permissions")
    public ResponseEntity<ApiResponse<RoleDTO>> updateRole(
            @PathVariable UUID orgId,
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request) {
        RoleDTO role = rbacService.updateRole(orgId, roleId, request);
        return ResponseEntity.ok(ApiResponse.success("Role updated successfully", role));
    }

    @DeleteMapping("/organizations/{orgId}/roles/{roleId}")
    @RequirePermission("role.manage")
    @Operation(summary = "Delete a custom role")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @PathVariable UUID orgId,
            @PathVariable UUID roleId) {
        rbacService.deleteRole(orgId, roleId);
        return ResponseEntity.ok(ApiResponse.success("Role deleted successfully", null));
    }

    @GetMapping("/permissions")
    @Operation(summary = "List all available granular system permissions")
    public ResponseEntity<ApiResponse<List<String>>> getAvailablePermissions() {
        return ResponseEntity.ok(ApiResponse.success(rbacService.getAllAvailablePermissions()));
    }
}
