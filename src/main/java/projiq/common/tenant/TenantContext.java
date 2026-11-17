package projiq.common.tenant;

import projiq.common.exception.BadRequestException;

import java.util.Optional;
import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Optional<UUID> getTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static UUID getRequiredTenantId() {
        return getTenantId().orElseThrow(() -> new BadRequestException("Organization context is required. Please specify X-Organization-Id header."));
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
