package projiq.organization.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projiq.common.response.ApiResponse;
import projiq.common.security.SecurityUtils;
import projiq.organization.dto.CreateOrganizationRequest;
import projiq.organization.dto.OrganizationDTO;
import projiq.organization.dto.UpdateOrganizationRequest;
import projiq.organization.service.OrganizationService;
import projiq.rbac.annotation.RequirePermission;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
@Tag(name = "Organizations", description = "Endpoints for managing workspace organizations and multi-tenancy")
@SecurityRequirement(name = "BearerAuth")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @Operation(summary = "Create a new organization workspace")
    public ResponseEntity<ApiResponse<OrganizationDTO>> createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        OrganizationDTO org = organizationService.createOrganization(request, userId);
        return new ResponseEntity<>(ApiResponse.success("Organization created successfully", org), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List all organizations the authenticated user belongs to")
    public ResponseEntity<ApiResponse<List<OrganizationDTO>>> getUserOrganizations() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<OrganizationDTO> orgs = organizationService.getUserOrganizations(userId);
        return ResponseEntity.ok(ApiResponse.success(orgs));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get organization details by ID")
    public ResponseEntity<ApiResponse<OrganizationDTO>> getOrganizationById(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        OrganizationDTO org = organizationService.getOrganizationById(id, userId);
        return ResponseEntity.ok(ApiResponse.success(org));
    }

    @PatchMapping("/{id}")
    @RequirePermission("organization.update")
    @Operation(summary = "Update organization settings")
    public ResponseEntity<ApiResponse<OrganizationDTO>> updateOrganization(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        OrganizationDTO org = organizationService.updateOrganization(id, request);
        return ResponseEntity.ok(ApiResponse.success("Organization updated successfully", org));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("organization.delete")
    @Operation(summary = "Delete an organization workspace")
    public ResponseEntity<ApiResponse<Void>> deleteOrganization(@PathVariable UUID id) {
        organizationService.deleteOrganization(id);
        return ResponseEntity.ok(ApiResponse.success("Organization deleted successfully", null));
    }
}
