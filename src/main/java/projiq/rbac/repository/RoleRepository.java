package projiq.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import projiq.organization.entity.Organization;
import projiq.rbac.entity.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByOrganizationAndName(Organization organization, String name);

    Optional<Role> findByOrganizationIdAndName(UUID organizationId, String name);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.organization.id = :orgId")
    List<Role> findByOrganizationIdWithPermissions(@Param("orgId") UUID orgId);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.id = :roleId")
    Optional<Role> findByIdWithPermissions(@Param("roleId") UUID roleId);

    boolean existsByOrganizationIdAndName(UUID organizationId, String name);
}
