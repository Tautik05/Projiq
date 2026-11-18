package projiq.membership.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import projiq.membership.entity.OrganizationMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    @Query("SELECT om FROM OrganizationMember om " +
            "JOIN FETCH om.role r " +
            "LEFT JOIN FETCH r.permissions " +
            "JOIN FETCH om.user u " +
            "WHERE om.organization.id = :orgId AND om.user.id = :userId")
    Optional<OrganizationMember> findByOrganizationIdAndUserIdWithRoleAndPermissions(
            @Param("orgId") UUID orgId,
            @Param("userId") UUID userId
    );

    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    @Query("SELECT om FROM OrganizationMember om " +
            "JOIN FETCH om.organization o " +
            "JOIN FETCH om.role r " +
            "WHERE om.user.id = :userId ORDER BY o.name ASC")
    List<OrganizationMember> findByUserIdWithOrganizationAndRole(@Param("userId") UUID userId);

    @Query("SELECT om FROM OrganizationMember om " +
            "JOIN FETCH om.user u " +
            "JOIN FETCH om.role r " +
            "WHERE om.organization.id = :orgId ORDER BY om.joinedAt ASC")
    List<OrganizationMember> findByOrganizationIdWithUserAndRole(@Param("orgId") UUID orgId);

    long countByOrganizationIdAndRoleName(UUID organizationId, String roleName);
}
