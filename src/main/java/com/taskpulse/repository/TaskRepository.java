package com.taskpulse.repository;

import com.taskpulse.entity.TaskEntity;
import com.taskpulse.enums.TaskPriority;
import com.taskpulse.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    @Query("SELECT t FROM TaskEntity t WHERE t.status IN ('PENDING', 'RETRYING') AND t.nextRunAt <= :currentTime ORDER BY t.priority ASC, t.nextRunAt ASC")
    List<TaskEntity> findDueTasks(@Param("currentTime") Instant currentTime, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TaskEntity t WHERE t.id = :id")
    Optional<TaskEntity> findByIdWithLock(@Param("id") UUID id);

    Page<TaskEntity> findByStatus(TaskStatus status, Pageable pageable);

    Page<TaskEntity> findByPriority(TaskPriority priority, Pageable pageable);

    Page<TaskEntity> findByStatusAndPriority(TaskStatus status, TaskPriority priority, Pageable pageable);

    long countByStatus(TaskStatus status);
}
