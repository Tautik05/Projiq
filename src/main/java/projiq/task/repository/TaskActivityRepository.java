package projiq.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import projiq.task.entity.TaskActivity;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskActivityRepository extends JpaRepository<TaskActivity, UUID> {

    @Query("SELECT ta FROM TaskActivity ta JOIN FETCH ta.actor WHERE ta.task.id = :taskId ORDER BY ta.createdAt DESC")
    List<TaskActivity> findByTaskIdWithActor(@Param("taskId") UUID taskId);
}
