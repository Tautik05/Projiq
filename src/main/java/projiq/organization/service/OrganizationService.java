package projiq.organization.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projiq.common.exception.ConflictException;
import projiq.common.exception.ForbiddenException;
import projiq.common.exception.ResourceNotFoundException;
import projiq.membership.entity.OrganizationMember;
import projiq.membership.repository.OrganizationMemberRepository;
import projiq.organization.dto.CreateOrganizationRequest;
import projiq.organization.dto.OrganizationDTO;
import projiq.organization.dto.UpdateOrganizationRequest;
import projiq.organization.entity.Organization;
import projiq.organization.repository.OrganizationRepository;
import projiq.rbac.entity.Role;
import projiq.rbac.service.RbacService;
import projiq.user.entity.User;
import projiq.user.repository.UserRepository;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OrganizationService {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final RbacService rbacService;
    private final projiq.audit.service.AuditService auditService;

    public OrganizationService(OrganizationRepository organizationRepository,
                               OrganizationMemberRepository memberRepository,
                               UserRepository userRepository,
                               RbacService rbacService,
                               projiq.audit.service.AuditService auditService) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.rbacService = rbacService;
        this.auditService = auditService;
    }

    @Transactional
    public OrganizationDTO createOrganization(CreateOrganizationRequest request, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        String slug = request.getSlug() != null && !request.getSlug().isBlank()
                ? slugify(request.getSlug())
                : generateUniqueSlug(request.getName());

        if (organizationRepository.existsBySlug(slug)) {
            throw new ConflictException("Organization with slug '" + slug + "' already exists");
        }

        Organization org = new Organization(request.getName().trim(), slug, request.getDescription(), user);
        org = organizationRepository.save(org);

        // Initialize default roles for this new organization
        Map<String, Role> defaultRoles = rbacService.initializeDefaultOrganizationRoles(org);
        Role ownerRole = defaultRoles.get("Owner");

        // Add creator as the initial Owner member
        OrganizationMember ownerMember = new OrganizationMember(org, user, ownerRole);
        memberRepository.save(ownerMember);

        auditService.logAction(org.getId(), userId, "ORGANIZATION_CREATED", "ORGANIZATION",
                org.getId().toString(), "Created workspace " + org.getName(), null);

        return OrganizationDTO.from(org, "Owner");
    }

    @Transactional(readOnly = true)
    public List<OrganizationDTO> getUserOrganizations(UUID userId) {
        List<OrganizationMember> memberships = memberRepository.findAll().stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .sorted(Comparator.comparing(m -> m.getOrganization().getName()))
                .collect(Collectors.toList());

        return memberships.stream()
                .map(m -> OrganizationDTO.from(m.getOrganization(), m.getRole().getName()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrganizationDTO getOrganizationById(UUID organizationId, UUID userId) {
        OrganizationMember member = memberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this organization"));

        return OrganizationDTO.from(member.getOrganization(), member.getRole().getName());
    }

    @Transactional
    public OrganizationDTO updateOrganization(UUID organizationId, UpdateOrganizationRequest request) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));

        if (request.getName() != null && !request.getName().isBlank()) {
            org.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            org.setDescription(request.getDescription());
        }

        org = organizationRepository.save(org);
        auditService.logAction(organizationId, null, "ORGANIZATION_UPDATED", "ORGANIZATION",
                organizationId.toString(), "Updated organization details", null);
        return OrganizationDTO.from(org);
    }

    @Transactional
    public void deleteOrganization(UUID organizationId) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));

        rbacService.invalidateOrganizationPermissions(organizationId);
        organizationRepository.delete(org);
    }

    private String generateUniqueSlug(String name) {
        String baseSlug = slugify(name);
        String slug = baseSlug;
        int counter = 1;
        while (organizationRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    private String slugify(String input) {
        if (input == null) return "org-" + UUID.randomUUID().toString().substring(0, 8);
        String noWhiteSpace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(noWhiteSpace, Normalizer.Form.NFD);
        String slug = NON_LATIN.matcher(normalized).replaceAll("").toLowerCase(Locale.ENGLISH);
        return slug.replaceAll("-+", "-").replaceAll("^-|-$", "");
    }
}
