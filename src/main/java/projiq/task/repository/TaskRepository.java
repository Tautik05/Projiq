package projiq.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import projiq.task.entity.Task;
import projiq.task.entity.TaskPriority;
import projiq.task.entity.TaskStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    @Query("SELECT t FROM Task t " +
            "LEFT JOIN FETCH t.assignee " +
            "LEFT JOIN FETCH t.createdBy " +
            "JOIN FETCH t.project " +
            "WHERE t.id = :taskId AND t.organization.id = :orgId")
    Optional<Task> findByIdAndOrganizationIdWithDetails(@Param("taskId") UUID taskId, @Param("orgId") UUID orgId);

    Optional<Task> findByIdAndOrganizationId(UUID taskId, UUID organizationId);

    @Query("SELECT t FROM Task t " +
            "LEFT JOIN FETCH t.assignee " +
            "WHERE t.organization.id = :orgId " +
            "AND (:projectId IS NULL OR t.project.id = :projectId) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:priority IS NULL OR t.priority = :priority) " +
            "AND (:assigneeId IS NULL OR t.assignee.id = :assigneeId) " +
            "ORDER BY t.createdAt DESC")
    List<Task> searchTasks(
            @Param("orgId") UUID orgId,
            @Param("projectId") UUID projectId,
            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority,
            @Param("assigneeId") UUID assigneeId
    );
}
