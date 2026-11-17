package projiq.membership.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import projiq.membership.entity.InvitationStatus;
import projiq.membership.entity.OrganizationInvitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationInvitationRepository extends JpaRepository<OrganizationInvitation, UUID> {

    @Query("SELECT oi FROM OrganizationInvitation oi " +
            "JOIN FETCH oi.organization o " +
            "JOIN FETCH oi.role r " +
            "JOIN FETCH oi.inviter u " +
            "WHERE oi.tokenHash = :tokenHash")
    Optional<OrganizationInvitation> findByTokenHashWithDetails(@Param("tokenHash") String tokenHash);

    List<OrganizationInvitation> findByOrganizationIdAndStatus(UUID organizationId, InvitationStatus status);

    boolean existsByOrganizationIdAndEmailIgnoreCaseAndStatus(UUID organizationId, String email, InvitationStatus status);
}
