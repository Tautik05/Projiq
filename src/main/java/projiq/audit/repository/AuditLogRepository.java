package projiq.audit.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import projiq.audit.entity.AuditLog;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("SELECT a FROM AuditLog a " +
            "LEFT JOIN FETCH a.actor " +
            "WHERE a.organization.id = :orgId " +
            "AND (:resourceType IS NULL OR a.resourceType = :resourceType) " +
            "AND (:action IS NULL OR a.action = :action)")
    Page<AuditLog> findByOrganizationIdAndFilters(
            @Param("orgId") UUID orgId,
            @Param("resourceType") String resourceType,
            @Param("action") String action,
            Pageable pageable
    );
}
