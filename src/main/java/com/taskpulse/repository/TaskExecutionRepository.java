package com.taskpulse.repository;

import com.taskpulse.entity.TaskExecutionEntity;
import com.taskpulse.enums.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, UUID> {

    List<TaskExecutionEntity> findByTaskIdOrderByStartedAtDesc(UUID taskId);

    Page<TaskExecutionEntity> findByTaskIdOrderByStartedAtDesc(UUID taskId, Pageable pageable);

    Page<TaskExecutionEntity> findByStatusOrderByStartedAtDesc(ExecutionStatus status, Pageable pageable);

    long countByStatus(ExecutionStatus status);
}
