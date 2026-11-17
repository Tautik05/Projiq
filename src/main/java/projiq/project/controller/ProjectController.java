package projiq.project.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projiq.common.response.ApiResponse;
import projiq.common.security.SecurityUtils;
import projiq.common.tenant.TenantContext;
import projiq.project.dto.*;
import projiq.project.entity.ProjectStatus;
import projiq.project.service.ProjectService;
import projiq.rbac.annotation.RequirePermission;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Endpoints for managing workspace projects and project members")
@SecurityRequirement(name = "BearerAuth")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @RequirePermission("project.create")
    @Operation(summary = "Create a new project within the organization")
    public ResponseEntity<ApiResponse<ProjectDTO>> createProject(@Valid @RequestBody CreateProjectRequest request) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        UUID creatorId = SecurityUtils.getCurrentUserId();
        ProjectDTO project = projectService.createProject(organizationId, request, creatorId);
        return new ResponseEntity<>(ApiResponse.success("Project created successfully", project), HttpStatus.CREATED);
    }

    @GetMapping
    @RequirePermission("project.read")
    @Operation(summary = "List or filter projects within the organization")
    public ResponseEntity<ApiResponse<List<ProjectDTO>>> getProjects(
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String search) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        List<ProjectDTO> projects = projectService.searchProjects(organizationId, status, search);
        return ResponseEntity.ok(ApiResponse.success(projects));
    }

    @GetMapping("/{id}")
    @RequirePermission("project.read")
    @Operation(summary = "Get project details by ID")
    public ResponseEntity<ApiResponse<ProjectDTO>> getProjectById(@PathVariable UUID id) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        ProjectDTO project = projectService.getProjectById(organizationId, id);
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @PatchMapping("/{id}")
    @RequirePermission("project.update")
    @Operation(summary = "Update project details, status, or manager")
    public ResponseEntity<ApiResponse<ProjectDTO>> updateProject(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest request) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        ProjectDTO project = projectService.updateProject(organizationId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Project updated successfully", project));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("project.delete")
    @Operation(summary = "Delete a project and its tasks")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable UUID id) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        projectService.deleteProject(organizationId, id);
        return ResponseEntity.ok(ApiResponse.success("Project deleted successfully", null));
    }

    @GetMapping("/{id}/members")
    @RequirePermission("project.read")
    @Operation(summary = "List all members assigned to a project")
    public ResponseEntity<ApiResponse<List<ProjectMemberDTO>>> getProjectMembers(@PathVariable UUID id) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        List<ProjectMemberDTO> members = projectService.getProjectMembers(organizationId, id);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @PostMapping("/{id}/members")
    @RequirePermission("project.update")
    @Operation(summary = "Add an organization member to the project")
    public ResponseEntity<ApiResponse<ProjectMemberDTO>> addProjectMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddProjectMemberRequest request) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        ProjectMemberDTO member = projectService.addProjectMember(organizationId, id, request);
        return new ResponseEntity<>(ApiResponse.success("Member added to project", member), HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @RequirePermission("project.update")
    @Operation(summary = "Remove a member from the project")
    public ResponseEntity<ApiResponse<Void>> removeProjectMember(
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        projectService.removeProjectMember(organizationId, id, userId);
        return ResponseEntity.ok(ApiResponse.success("Member removed from project", null));
    }
}
