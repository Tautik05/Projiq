package projiq.audit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import projiq.audit.dto.AuditLogDTO;
import projiq.audit.service.AuditService;
import projiq.common.response.ApiResponse;
import projiq.common.response.PagedResponse;
import projiq.common.tenant.TenantContext;
import projiq.rbac.annotation.RequirePermission;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit Logs", description = "Endpoints for viewing append-only organization activity logs")
@SecurityRequirement(name = "BearerAuth")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @RequirePermission("audit.read")
    @Operation(summary = "Get paginated audit logs for the organization")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDTO>>> getAuditLogs(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        PagedResponse<AuditLogDTO> response = auditService.getAuditLogs(organizationId, resourceType, action, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
