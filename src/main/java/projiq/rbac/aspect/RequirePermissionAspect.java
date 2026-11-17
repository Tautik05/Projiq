package projiq.rbac.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import projiq.common.exception.ForbiddenException;
import projiq.common.security.SecurityUtils;
import projiq.common.tenant.TenantContext;
import projiq.rbac.annotation.RequirePermission;
import projiq.rbac.service.RbacService;

import java.util.UUID;

@Aspect
@Component
public class RequirePermissionAspect {

    private final RbacService rbacService;

    public RequirePermissionAspect(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID organizationId = TenantContext.getRequiredTenantId();

        String permission = requirePermission.value();
        if (!rbacService.hasPermission(organizationId, userId, permission)) {
            throw new ForbiddenException("Access denied. You do not have the required permission: '" + permission + "' in this organization.");
        }

        return joinPoint.proceed();
    }
}
