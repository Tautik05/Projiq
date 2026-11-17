package projiq.task.controller;

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
import projiq.rbac.annotation.RequirePermission;
import projiq.task.dto.*;
import projiq.task.entity.TaskPriority;
import projiq.task.entity.TaskStatus;
import projiq.task.service.TaskService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Endpoints for managing project tasks, comments, and activity histories")
@SecurityRequirement(name = "BearerAuth")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @RequirePermission("task.create")
    @Operation(summary = "Create a new task within a project")
    public ResponseEntity<ApiResponse<TaskDTO>> createTask(@Valid @RequestBody CreateTaskRequest request) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        UUID creatorId = SecurityUtils.getCurrentUserId();
        TaskDTO task = taskService.createTask(organizationId, request, creatorId);
        return new ResponseEntity<>(ApiResponse.success("Task created successfully", task), HttpStatus.CREATED);
    }

    @GetMapping
    @RequirePermission("task.read")
    @Operation(summary = "List and filter tasks within the workspace")
    public ResponseEntity<ApiResponse<List<TaskDTO>>> getTasks(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) UUID assigneeId) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        List<TaskDTO> tasks = taskService.searchTasks(organizationId, projectId, status, priority, assigneeId);
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @GetMapping("/{id}")
    @RequirePermission("task.read")
    @Operation(summary = "Get task details by ID")
    public ResponseEntity<ApiResponse<TaskDTO>> getTaskById(@PathVariable UUID id) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        TaskDTO task = taskService.getTaskById(organizationId, id);
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @PatchMapping("/{id}")
    @RequirePermission("task.update")
    @Operation(summary = "Update task status, priority, description, or assignee")
    public ResponseEntity<ApiResponse<TaskDTO>> updateTask(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest request) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        UUID actorId = SecurityUtils.getCurrentUserId();
        TaskDTO task = taskService.updateTask(organizationId, id, request, actorId);
        return ResponseEntity.ok(ApiResponse.success("Task updated successfully", task));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("task.delete")
    @Operation(summary = "Delete a task")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable UUID id) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        taskService.deleteTask(organizationId, id);
        return ResponseEntity.ok(ApiResponse.success("Task deleted successfully", null));
    }

    @PostMapping("/{id}/comments")
    @RequirePermission("task.read")
    @Operation(summary = "Add a comment to a task")
    public ResponseEntity<ApiResponse<TaskCommentDTO>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTaskCommentRequest request) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        UUID userId = SecurityUtils.getCurrentUserId();
        TaskCommentDTO comment = taskService.addComment(organizationId, id, request, userId);
        return new ResponseEntity<>(ApiResponse.success("Comment added", comment), HttpStatus.CREATED);
    }

    @GetMapping("/{id}/comments")
    @RequirePermission("task.read")
    @Operation(summary = "Get all comments for a task")
    public ResponseEntity<ApiResponse<List<TaskCommentDTO>>> getComments(@PathVariable UUID id) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        List<TaskCommentDTO> comments = taskService.getComments(organizationId, id);
        return ResponseEntity.ok(ApiResponse.success(comments));
    }

    @GetMapping("/{id}/activities")
    @RequirePermission("task.read")
    @Operation(summary = "Get activity and change history for a task")
    public ResponseEntity<ApiResponse<List<TaskActivityDTO>>> getActivities(@PathVariable UUID id) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        List<TaskActivityDTO> activities = taskService.getActivities(organizationId, id);
        return ResponseEntity.ok(ApiResponse.success(activities));
    }
}
