package projiq.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import projiq.organization.entity.Organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("SELECT om.organization FROM OrganizationMember om WHERE om.user.id = :userId ORDER BY om.organization.name ASC")
    List<Organization> findOrganizationsByUserId(@Param("userId") UUID userId);
}
