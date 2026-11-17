package projiq.rbac.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projiq.common.exception.BadRequestException;
import projiq.common.exception.ConflictException;
import projiq.common.exception.ForbiddenException;
import projiq.common.exception.ResourceNotFoundException;
import projiq.membership.entity.OrganizationMember;
import projiq.membership.repository.OrganizationMemberRepository;
import projiq.organization.entity.Organization;
import projiq.rbac.dto.CreateRoleRequest;
import projiq.rbac.dto.RoleDTO;
import projiq.rbac.dto.UpdateRoleRequest;
import projiq.rbac.entity.Permission;
import projiq.rbac.entity.Role;
import projiq.rbac.repository.PermissionRepository;
import projiq.rbac.repository.RoleRepository;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RbacService {

    private static final Logger log = LoggerFactory.getLogger(RbacService.class);
    private static final String REDIS_PERMS_KEY_PREFIX = "rbac:perms:";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final OrganizationMemberRepository memberRepository;
    private final StringRedisTemplate redisTemplate;

    public RbacService(RoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       OrganizationMemberRepository memberRepository,
                       StringRedisTemplate redisTemplate) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.memberRepository = memberRepository;
        this.redisTemplate = redisTemplate;
    }

    public boolean hasPermission(UUID organizationId, UUID userId, String requiredPermission) {
        Set<String> permissions = getUserPermissions(organizationId, userId);
        return permissions.contains(requiredPermission);
    }

    public Set<String> getUserPermissions(UUID organizationId, UUID userId) {
        String cacheKey = REDIS_PERMS_KEY_PREFIX + organizationId + ":" + userId;

        try {
            Set<String> cached = redisTemplate.opsForSet().members(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis read failed for user permissions [org={}, user={}]: {}", organizationId, userId, e.getMessage());
        }

        // Cache miss: query database
        Optional<OrganizationMember> memberOpt = memberRepository.findByOrganizationIdAndUserIdWithRoleAndPermissions(organizationId, userId);
        if (memberOpt.isEmpty()) {
            return Collections.emptySet();
        }

        Role role = memberOpt.get().getRole();
        Set<String> permissions = role.getPermissions().stream()
                .map(Permission::getName)
                .collect(Collectors.toSet());

        // Cache in Redis (15 minutes TTL)
        try {
            if (!permissions.isEmpty()) {
                redisTemplate.opsForSet().add(cacheKey, permissions.toArray(new String[0]));
                redisTemplate.expire(cacheKey, Duration.ofMinutes(15));
            }
        } catch (Exception e) {
            log.warn("Redis write failed for permissions [org={}, user={}]: {}", organizationId, userId, e.getMessage());
        }

        return permissions;
    }

    public void invalidateUserPermissions(UUID organizationId, UUID userId) {
        String cacheKey = REDIS_PERMS_KEY_PREFIX + organizationId + ":" + userId;
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("Redis eviction failed for [{}]: {}", cacheKey, e.getMessage());
        }
    }

    public void invalidateOrganizationPermissions(UUID organizationId) {
        String pattern = REDIS_PERMS_KEY_PREFIX + organizationId + ":*";
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis eviction failed for pattern [{}]: {}", pattern, e.getMessage());
        }
    }

    @Transactional
    public Map<String, Role> initializeDefaultOrganizationRoles(Organization organization) {
        List<Permission> allPermissions = permissionRepository.findAll();
        Map<String, Permission> permMap = allPermissions.stream()
                .collect(Collectors.toMap(Permission::getName, p -> p));

        // 1. Owner: All permissions
        Role ownerRole = new Role(organization, "Owner", "Full workspace ownership and administrative access", true);
        ownerRole.setPermissions(new HashSet<>(allPermissions));
        ownerRole = roleRepository.save(ownerRole);

        // 2. Admin: All permissions except deleting the organization
        Role adminRole = new Role(organization, "Admin", "Administrative workspace access", true);
        Set<Permission> adminPerms = allPermissions.stream()
                .filter(p -> !p.getName().equals("organization.delete"))
                .collect(Collectors.toSet());
        adminRole.setPermissions(adminPerms);
        adminRole = roleRepository.save(adminRole);

        // 3. Member: Project and Task collaboration
        Role memberRole = new Role(organization, "Member", "Standard project and task member", true);
        Set<String> memberPermNames = Set.of(
                "organization.read", "project.read",
                "task.create", "task.read", "task.assign", "task.update"
        );
        Set<Permission> memberPerms = memberPermNames.stream()
                .map(permMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        memberRole.setPermissions(memberPerms);
        memberRole = roleRepository.save(memberRole);

        // 4. Viewer: Read-only access
        Role viewerRole = new Role(organization, "Viewer", "Read-only access to projects and tasks", true);
        Set<String> viewerPermNames = Set.of("organization.read", "project.read", "task.read");
        Set<Permission> viewerPerms = viewerPermNames.stream()
                .map(permMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        viewerRole.setPermissions(viewerPerms);
        viewerRole = roleRepository.save(viewerRole);

        Map<String, Role> map = new HashMap<>();
        map.put("Owner", ownerRole);
        map.put("Admin", adminRole);
        map.put("Member", memberRole);
        map.put("Viewer", viewerRole);
        return map;
    }

    @Transactional(readOnly = true)
    public List<RoleDTO> getOrganizationRoles(UUID organizationId) {
        return roleRepository.findByOrganizationIdWithPermissions(organizationId).stream()
                .map(RoleDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoleDTO createCustomRole(UUID organizationId, CreateRoleRequest request, Organization organization) {
        String roleName = request.getName().trim();

        if (roleRepository.existsByOrganizationIdAndName(organizationId, roleName)) {
            throw new ConflictException("A role named '" + roleName + "' already exists in this organization");
        }

        Role role = new Role(organization, roleName, request.getDescription(), false);

        if (request.getPermissions() != null && !request.getPermissions().isEmpty()) {
            Set<Permission> perms = permissionRepository.findByNameIn(request.getPermissions());
            role.setPermissions(perms);
        }

        role = roleRepository.save(role);
        return RoleDTO.from(role);
    }

    @Transactional
    public RoleDTO updateRole(UUID organizationId, UUID roleId, UpdateRoleRequest request) {
        Role role = roleRepository.findByIdWithPermissions(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));

        if (!Objects.equals(role.getOrganization().getId(), organizationId)) {
            throw new ForbiddenException("Role does not belong to this organization");
        }

        if (role.isSystemRole() && "Owner".equalsIgnoreCase(role.getName())) {
            throw new BadRequestException("The Owner role permissions cannot be modified");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            String newName = request.getName().trim();
            if (!newName.equalsIgnoreCase(role.getName()) && roleRepository.existsByOrganizationIdAndName(organizationId, newName)) {
                throw new ConflictException("A role with name '" + newName + "' already exists");
            }
            if (role.isSystemRole()) {
                throw new BadRequestException("System role names cannot be renamed");
            }
            role.setName(newName);
        }

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        if (request.getPermissions() != null) {
            Set<Permission> perms = permissionRepository.findByNameIn(request.getPermissions());
            role.setPermissions(perms);
        }

        role = roleRepository.save(role);

        // Invalidate Redis permissions cache for all members in this organization
        invalidateOrganizationPermissions(organizationId);

        return RoleDTO.from(role);
    }

    @Transactional
    public void deleteRole(UUID organizationId, UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));

        if (!Objects.equals(role.getOrganization().getId(), organizationId)) {
            throw new ForbiddenException("Role does not belong to this organization");
        }

        if (role.isSystemRole()) {
            throw new BadRequestException("System roles cannot be deleted");
        }

        roleRepository.delete(role);
        invalidateOrganizationPermissions(organizationId);
    }

    @Transactional(readOnly = true)
    public List<String> getAllAvailablePermissions() {
        return permissionRepository.findAll().stream()
                .map(Permission::getName)
                .sorted()
                .collect(Collectors.toList());
    }
}
