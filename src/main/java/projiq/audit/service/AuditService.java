package projiq.audit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projiq.audit.dto.AuditLogDTO;
import projiq.audit.entity.AuditLog;
import projiq.audit.repository.AuditLogRepository;
import projiq.common.exception.ResourceNotFoundException;
import projiq.common.response.PagedResponse;
import projiq.organization.entity.Organization;
import projiq.organization.repository.OrganizationRepository;
import projiq.user.entity.User;
import projiq.user.repository.UserRepository;

import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository,
                        OrganizationRepository organizationRepository,
                        UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void logAction(UUID organizationId, UUID actorId, String action, String resourceType, String resourceId, String details, String ipAddress) {
        try {
            Organization organization = organizationRepository.findById(organizationId).orElse(null);
            if (organization == null) {
                return;
            }

            User actor = actorId != null ? userRepository.findById(actorId).orElse(null) : null;

            AuditLog auditLog = new AuditLog(organization, actor, action, resourceType, resourceId, details, ipAddress);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to persist audit log [org={}, action={}]: {}", organizationId, action, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogDTO> getAuditLogs(UUID organizationId, String resourceType, String action, int page, int size) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", "id", organizationId);
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by("createdAt").descending());
        Page<AuditLogDTO> auditPage = auditLogRepository.findByOrganizationIdAndFilters(organizationId, resourceType, action, pageable)
                .map(AuditLogDTO::from);

        return PagedResponse.from(auditPage);
    }
}
