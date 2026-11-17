package projiq.common.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import projiq.common.exception.BadRequestException;
import projiq.common.exception.ForbiddenException;
import projiq.common.security.SecurityUtils;
import projiq.membership.repository.OrganizationMemberRepository;

import java.util.UUID;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    public static final String TENANT_HEADER = "X-Organization-Id";

    private final OrganizationMemberRepository memberRepository;

    public TenantInterceptor(OrganizationMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String orgHeader = request.getHeader(TENANT_HEADER);

        if (StringUtils.hasText(orgHeader)) {
            UUID organizationId;
            try {
                organizationId = UUID.fromString(orgHeader.trim());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid X-Organization-Id header format. Must be a valid UUID.");
            }

            SecurityUtils.getCurrentUserPrincipal().ifPresent(principal -> {
                UUID userId = principal.getId();
                if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
                    throw new ForbiddenException("Access denied: You are not a member of organization " + organizationId);
                }
            });
            TenantContext.setTenantId(organizationId);
        }

        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                @Nullable Exception ex) {
        TenantContext.clear();
    }
}
