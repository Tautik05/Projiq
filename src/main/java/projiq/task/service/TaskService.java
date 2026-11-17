package projiq.task.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projiq.common.exception.BadRequestException;
import projiq.common.exception.ResourceNotFoundException;
import projiq.membership.repository.OrganizationMemberRepository;
import projiq.organization.entity.Organization;
import projiq.project.entity.Project;
import projiq.project.repository.ProjectRepository;
import projiq.task.dto.*;
import projiq.task.entity.*;
import projiq.task.repository.TaskActivityRepository;
import projiq.task.repository.TaskCommentRepository;
import projiq.task.repository.TaskRepository;
import projiq.user.entity.User;
import projiq.user.repository.UserRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskCommentRepository commentRepository;
    private final TaskActivityRepository activityRepository;
    private final ProjectRepository projectRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserRepository userRepository;
    private final projiq.audit.service.AuditService auditService;
    private final projiq.notification.service.NotificationService notificationService;

    public TaskService(TaskRepository taskRepository,
                       TaskCommentRepository commentRepository,
                       TaskActivityRepository activityRepository,
                       ProjectRepository projectRepository,
                       OrganizationMemberRepository organizationMemberRepository,
                       UserRepository userRepository,
                       projiq.audit.service.AuditService auditService,
                       projiq.notification.service.NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.commentRepository = commentRepository;
        this.activityRepository = activityRepository;
        this.projectRepository = projectRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public TaskDTO createTask(UUID organizationId, CreateTaskRequest request, UUID creatorId) {
        Project project = projectRepository.findByIdAndOrganizationId(request.getProjectId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", request.getProjectId()));

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", creatorId));

        User assignee = null;
        if (request.getAssigneeId() != null) {
            assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User (Assignee)", "id", request.getAssigneeId()));

            if (!organizationMemberRepository.existsByOrganizationIdAndUserId(organizationId, assignee.getId())) {
                throw new BadRequestException("Selected assignee is not a member of this organization");
            }
        }

        Organization org = project.getOrganization();
        Task task = new Task(
                org,
                project,
                request.getTitle().trim(),
                request.getDescription(),
                request.getStatus(),
                request.getPriority(),
                request.getDueDate(),
                assignee,
                creator
        );
        task = taskRepository.save(task);

        // Record initial activity
        TaskActivity activity = new TaskActivity(task, creator, "CREATED", "Task created");
        activityRepository.save(activity);

        auditService.logAction(organizationId, creatorId, "TASK_CREATED", "TASK",
                task.getId().toString(), "Created task: " + task.getTitle(), null);

        if (assignee != null && !assignee.getId().equals(creator.getId())) {
            notificationService.createNotification(
                    assignee.getId(),
                    organizationId,
                    "Task Assigned",
                    "You have been assigned to task: " + task.getTitle(),
                    "TASK_ASSIGNED",
                    task.getId().toString()
            );
        }

        return TaskDTO.from(task);
    }

    @Transactional(readOnly = true)
    public List<TaskDTO> searchTasks(UUID organizationId, UUID projectId, TaskStatus status, TaskPriority priority, UUID assigneeId) {
        return taskRepository.searchTasks(organizationId, projectId, status, priority, assigneeId).stream()
                .map(TaskDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TaskDTO getTaskById(UUID organizationId, UUID taskId) {
        Task task = taskRepository.findByIdAndOrganizationIdWithDetails(taskId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        return TaskDTO.from(task);
    }

    @Transactional
    public TaskDTO updateTask(UUID organizationId, UUID taskId, UpdateTaskRequest request, UUID actorId) {
        Task task = taskRepository.findByIdAndOrganizationIdWithDetails(taskId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorId));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            task.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }

        // Track Status Change
        if (request.getStatus() != null && request.getStatus() != task.getStatus()) {
            TaskStatus oldStatus = task.getStatus();
            task.setStatus(request.getStatus());
            TaskActivity activity = new TaskActivity(task, actor, "STATUS_CHANGED",
                    String.format("Status changed from %s to %s", oldStatus, request.getStatus()));
            activityRepository.save(activity);
        }

        // Track Priority Change
        if (request.getPriority() != null && request.getPriority() != task.getPriority()) {
            TaskPriority oldPriority = task.getPriority();
            task.setPriority(request.getPriority());
            TaskActivity activity = new TaskActivity(task, actor, "PRIORITY_CHANGED",
                    String.format("Priority changed from %s to %s", oldPriority, request.getPriority()));
            activityRepository.save(activity);
        }

        // Track Assignee Change
        if (request.getAssigneeId() != null) {
            if (task.getAssignee() == null || !task.getAssignee().getId().equals(request.getAssigneeId())) {
                User newAssignee = userRepository.findById(request.getAssigneeId())
                        .orElseThrow(() -> new ResourceNotFoundException("User (Assignee)", "id", request.getAssigneeId()));

                if (!organizationMemberRepository.existsByOrganizationIdAndUserId(organizationId, newAssignee.getId())) {
                    throw new BadRequestException("Selected assignee is not a member of this organization");
                }

                task.setAssignee(newAssignee);
                TaskActivity activity = new TaskActivity(task, actor, "ASSIGNED",
                        "Task assigned to " + newAssignee.getFullName());
                activityRepository.save(activity);

                notificationService.createNotification(
                        newAssignee.getId(),
                        organizationId,
                        "Task Assigned",
                        "You were assigned to task: " + task.getTitle(),
                        "TASK_ASSIGNED",
                        task.getId().toString()
                );
            }
        }

        task = taskRepository.save(task);
        return TaskDTO.from(task);
    }

    @Transactional
    public void deleteTask(UUID organizationId, UUID taskId) {
        Task task = taskRepository.findByIdAndOrganizationId(taskId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        taskRepository.delete(task);
    }

    @Transactional
    public TaskCommentDTO addComment(UUID organizationId, UUID taskId, CreateTaskCommentRequest request, UUID userId) {
        Task task = taskRepository.findByIdAndOrganizationId(taskId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        TaskComment comment = new TaskComment(task, user, request.getContent().trim());
        comment = commentRepository.save(comment);

        TaskActivity activity = new TaskActivity(task, user, "COMMENTED", "Added a comment");
        activityRepository.save(activity);

        return TaskCommentDTO.from(comment);
    }

    @Transactional(readOnly = true)
    public List<TaskCommentDTO> getComments(UUID organizationId, UUID taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new ResourceNotFoundException("Task", "id", taskId);
        }

        return commentRepository.findByTaskIdWithUser(taskId).stream()
                .map(TaskCommentDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TaskActivityDTO> getActivities(UUID organizationId, UUID taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new ResourceNotFoundException("Task", "id", taskId);
        }

        return activityRepository.findByTaskIdWithActor(taskId).stream()
                .map(TaskActivityDTO::from)
                .collect(Collectors.toList());
    }
}
