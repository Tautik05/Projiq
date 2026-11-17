package projiq.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import projiq.task.entity.TaskComment;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

    @Query("SELECT tc FROM TaskComment tc JOIN FETCH tc.user WHERE tc.task.id = :taskId ORDER BY tc.createdAt ASC")
    List<TaskComment> findByTaskIdWithUser(@Param("taskId") UUID taskId);
}
