package projiq.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import projiq.project.entity.Project;
import projiq.project.entity.ProjectStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("SELECT p FROM Project p " +
            "LEFT JOIN FETCH p.manager " +
            "LEFT JOIN FETCH p.createdBy " +
            "WHERE p.id = :id AND p.organization.id = :orgId")
    Optional<Project> findByIdAndOrganizationIdWithDetails(@Param("id") UUID id, @Param("orgId") UUID orgId);

    Optional<Project> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("SELECT p FROM Project p " +
            "LEFT JOIN FETCH p.manager " +
            "WHERE p.organization.id = :orgId " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY p.createdAt DESC")
    List<Project> searchProjects(
            @Param("orgId") UUID orgId,
            @Param("status") ProjectStatus status,
            @Param("search") String search
    );

    boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);
}
