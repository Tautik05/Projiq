package projiq.project.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projiq.common.exception.BadRequestException;
import projiq.common.exception.ConflictException;
import projiq.common.exception.ResourceNotFoundException;
import projiq.membership.repository.OrganizationMemberRepository;
import projiq.organization.entity.Organization;
import projiq.organization.repository.OrganizationRepository;
import projiq.project.dto.*;
import projiq.project.entity.Project;
import projiq.project.entity.ProjectMember;
import projiq.project.entity.ProjectStatus;
import projiq.project.repository.ProjectMemberRepository;
import projiq.project.repository.ProjectRepository;
import projiq.user.entity.User;
import projiq.user.repository.UserRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectMemberRepository projectMemberRepository,
                          OrganizationRepository organizationRepository,
                          OrganizationMemberRepository organizationMemberRepository,
                          UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProjectDTO createProject(UUID organizationId, CreateProjectRequest request, UUID creatorId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", creatorId));

        User manager = null;
        if (request.getManagerId() != null) {
            manager = userRepository.findById(request.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("User (Manager)", "id", request.getManagerId()));

            // Verify manager is a member of this organization
            if (!organizationMemberRepository.existsByOrganizationIdAndUserId(organizationId, manager.getId())) {
                throw new BadRequestException("Selected project manager is not a member of this organization");
            }
        }

        Project project = new Project(
                organization,
                request.getName().trim(),
                request.getDescription(),
                request.getStatus(),
                manager,
                request.getDueDate(),
                creator
        );
        project = projectRepository.save(project);

        // Add creator as Project Lead
        ProjectMember creatorMember = new ProjectMember(project, creator, "LEAD");
        projectMemberRepository.save(creatorMember);

        // If manager is set and different from creator, add manager as MANAGER
        if (manager != null && !manager.getId().equals(creator.getId())) {
            ProjectMember managerMember = new ProjectMember(project, manager, "MANAGER");
            projectMemberRepository.save(managerMember);
        }

        return ProjectDTO.from(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectDTO> searchProjects(UUID organizationId, ProjectStatus status, String search) {
        List<Project> projects;
        if (search != null && !search.trim().isEmpty()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            projects = projectRepository.searchByOrganizationIdAndStatusAndName(organizationId, status, pattern);
        } else {
            projects = projectRepository.findByOrganizationIdAndStatus(organizationId, status);
        }

        return projects.stream()
                .map(ProjectDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectDTO getProjectById(UUID organizationId, UUID projectId) {
        Project project = projectRepository.findByIdAndOrganizationIdWithDetails(projectId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        return ProjectDTO.from(project);
    }

    @Transactional
    public ProjectDTO updateProject(UUID organizationId, UUID projectId, UpdateProjectRequest request) {
        Project project = projectRepository.findByIdAndOrganizationIdWithDetails(projectId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (request.getName() != null && !request.getName().isBlank()) {
            project.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            project.setStatus(request.getStatus());
        }
        if (request.getDueDate() != null) {
            project.setDueDate(request.getDueDate());
        }
        if (request.getManagerId() != null) {
            User manager = userRepository.findById(request.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("User (Manager)", "id", request.getManagerId()));

            if (!organizationMemberRepository.existsByOrganizationIdAndUserId(organizationId, manager.getId())) {
                throw new BadRequestException("Selected project manager is not a member of this organization");
            }
            project.setManager(manager);

            // Ensure new manager is a project member
            if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, manager.getId())) {
                ProjectMember pm = new ProjectMember(project, manager, "MANAGER");
                projectMemberRepository.save(pm);
            }
        }

        project = projectRepository.save(project);
        return ProjectDTO.from(project);
    }

    @Transactional
    public void deleteProject(UUID organizationId, UUID projectId) {
        Project project = projectRepository.findByIdAndOrganizationId(projectId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        projectRepository.delete(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberDTO> getProjectMembers(UUID organizationId, UUID projectId) {
        if (!projectRepository.existsByIdAndOrganizationId(projectId, organizationId)) {
            throw new ResourceNotFoundException("Project", "id", projectId);
        }

        return projectMemberRepository.findByProjectIdWithUser(projectId).stream()
                .map(ProjectMemberDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProjectMemberDTO addProjectMember(UUID organizationId, UUID projectId, AddProjectMemberRequest request) {
        Project project = projectRepository.findByIdAndOrganizationId(projectId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));

        // Check if user is a member of the organization
        if (!organizationMemberRepository.existsByOrganizationIdAndUserId(organizationId, user.getId())) {
            throw new BadRequestException("User must be a member of the organization before joining a project");
        }

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
            throw new ConflictException("User is already a member of this project");
        }

        ProjectMember pm = new ProjectMember(project, user, request.getRole());
        pm = projectMemberRepository.save(pm);

        return ProjectMemberDTO.from(pm);
    }

    @Transactional
    public void removeProjectMember(UUID organizationId, UUID projectId, UUID userId) {
        if (!projectRepository.existsByIdAndOrganizationId(projectId, organizationId)) {
            throw new ResourceNotFoundException("Project", "id", projectId);
        }

        ProjectMember pm = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectMember", "userId", userId));

        projectMemberRepository.delete(pm);
    }
}
