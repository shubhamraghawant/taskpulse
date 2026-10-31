package com.taskpulse.service;

import com.taskpulse.dto.*;
import com.taskpulse.entity.TaskEntity;
import com.taskpulse.entity.TaskExecutionEntity;
import com.taskpulse.enums.ExecutionStatus;
import com.taskpulse.enums.TaskPriority;
import com.taskpulse.enums.TaskStatus;
import com.taskpulse.enums.TaskType;
import com.taskpulse.repository.TaskExecutionRepository;
import com.taskpulse.repository.TaskRepository;
import com.taskpulse.worker.PriorityWorkerPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskExecutionRepository executionRepository;
    private final LeaderElectionService leaderElectionService;
    private final PriorityWorkerPool workerPool;
    private final TaskExecutorService executorService;

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        Instant nextRunAt;

        if (request.getType() == TaskType.CRON) {
            if (request.getCronExpression() == null || request.getCronExpression().isBlank()) {
                throw new IllegalArgumentException("Cron expression is required for CRON task type");
            }
            if (!CronExpression.isValidExpression(request.getCronExpression())) {
                throw new IllegalArgumentException("Invalid Cron expression: " + request.getCronExpression());
            }
            CronExpression cron = CronExpression.parse(request.getCronExpression());
            ZonedDateTime nextZoned = cron.next(ZonedDateTime.now(ZoneId.systemDefault()));
            if (nextZoned == null) {
                throw new IllegalArgumentException("Cron expression will never fire in the future");
            }
            nextRunAt = nextZoned.toInstant();
        } else if (request.getType() == TaskType.DELAYED) {
            if (request.getScheduledAt() == null) {
                throw new IllegalArgumentException("scheduledAt timestamp is required for DELAYED task type");
            }
            nextRunAt = request.getScheduledAt();
        } else {
            // ONE_OFF fires immediately
            nextRunAt = Instant.now();
        }

        TaskEntity entity = TaskEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .cronExpression(request.getCronExpression())
                .priority(request.getPriority() != null ? request.getPriority() : TaskPriority.NORMAL)
                .status(TaskStatus.PENDING)
                .payload(request.getPayload())
                .maxRetries(request.getMaxRetries() > 0 ? request.getMaxRetries() : 3)
                .timeoutSeconds(request.getTimeoutSeconds() > 0 ? request.getTimeoutSeconds() : 300)
                .nextRunAt(nextRunAt)
                .build();

        TaskEntity saved = taskRepository.save(entity);
        log.info("Created task '{}' [ID: {}] scheduled for {}", saved.getName(), saved.getId(), saved.getNextRunAt());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID id) {
        TaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + id));
        return mapToResponse(entity);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> listTasks(TaskStatus status, TaskPriority priority, Pageable pageable) {
        Page<TaskEntity> page;
        if (status != null && priority != null) {
            page = taskRepository.findByStatusAndPriority(status, priority, pageable);
        } else if (status != null) {
            page = taskRepository.findByStatus(status, pageable);
        } else if (priority != null) {
            page = taskRepository.findByPriority(priority, pageable);
        } else {
            page = taskRepository.findAll(pageable);
        }
        return page.map(this::mapToResponse);
    }

    @Transactional
    public TaskResponse triggerTaskImmediately(UUID id) {
        TaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + id));

        entity.setNextRunAt(Instant.now());
        entity.setStatus(TaskStatus.PENDING);
        TaskEntity saved = taskRepository.save(entity);

        // Submit directly to worker pool
        workerPool.submit(saved, () -> executorService.executeTask(saved.getId()));
        log.info("Manually triggered immediate execution for task '{}'", entity.getName());
        return mapToResponse(saved);
    }

    @Transactional
    public TaskResponse pauseTask(UUID id) {
        TaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + id));
        entity.setStatus(TaskStatus.PAUSED);
        return mapToResponse(taskRepository.save(entity));
    }

    @Transactional
    public TaskResponse resumeTask(UUID id) {
        TaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + id));
        entity.setStatus(TaskStatus.PENDING);
        if (entity.getNextRunAt() == null || entity.getNextRunAt().isBefore(Instant.now())) {
            entity.setNextRunAt(Instant.now());
        }
        return mapToResponse(taskRepository.save(entity));
    }

    @Transactional
    public void deleteTask(UUID id) {
        if (!taskRepository.existsById(id)) {
            throw new IllegalArgumentException("Task not found with ID: " + id);
        }
        taskRepository.deleteById(id);
        log.info("Deleted task ID: {}", id);
    }

    @Transactional(readOnly = true)
    public Page<TaskExecutionResponse> getExecutionsForTask(UUID taskId, Pageable pageable) {
        return executionRepository.findByTaskIdOrderByStartedAtDesc(taskId, pageable)
                .map(this::mapToExecutionResponse);
    }

    @Transactional(readOnly = true)
    public Page<TaskExecutionResponse> listAllExecutions(ExecutionStatus status, Pageable pageable) {
        if (status != null) {
            return executionRepository.findByStatusOrderByStartedAtDesc(status, pageable)
                    .map(this::mapToExecutionResponse);
        }
        return executionRepository.findAll(pageable).map(this::mapToExecutionResponse);
    }

    @Transactional(readOnly = true)
    public ClusterStatusResponse getClusterStatus() {
        Map<String, Long> taskStatusCounts = new HashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            taskStatusCounts.put(status.name(), taskRepository.countByStatus(status));
        }

        Map<String, Long> executionStatusCounts = new HashMap<>();
        for (ExecutionStatus status : ExecutionStatus.values()) {
            executionStatusCounts.put(status.name(), executionRepository.countByStatus(status));
        }

        var leaseInfo = leaderElectionService.getCurrentLeaseInfo();
        String currentLeader = leaseInfo.map(l -> l.getLockedByNode()).orElse("unknown");
        Instant leaseExpires = leaseInfo.map(l -> l.getExpiresAt()).orElse(Instant.EPOCH);

        return ClusterStatusResponse.builder()
                .nodeId(leaderElectionService.getNodeId())
                .leader(leaderElectionService.isLeader())
                .currentLeaderNode(currentLeader)
                .leaderLeaseExpiresAt(leaseExpires)
                .activeWorkerThreads(workerPool.getActiveWorkerCount())
                .queueDepth(workerPool.getQueueDepth())
                .taskStatusCounts(taskStatusCounts)
                .executionStatusCounts(executionStatusCounts)
                .timestamp(Instant.now())
                .build();
    }

    private TaskResponse mapToResponse(TaskEntity entity) {
        return TaskResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .type(entity.getType())
                .cronExpression(entity.getCronExpression())
                .priority(entity.getPriority())
                .status(entity.getStatus())
                .payload(entity.getPayload())
                .maxRetries(entity.getMaxRetries())
                .currentRetryCount(entity.getCurrentRetryCount())
                .timeoutSeconds(entity.getTimeoutSeconds())
                .nextRunAt(entity.getNextRunAt())
                .lastRunAt(entity.getLastRunAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private TaskExecutionResponse mapToExecutionResponse(TaskExecutionEntity entity) {
        return TaskExecutionResponse.builder()
                .id(entity.getId())
                .taskId(entity.getTaskId())
                .nodeInstanceId(entity.getNodeInstanceId())
                .attemptNumber(entity.getAttemptNumber())
                .status(entity.getStatus())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .durationMs(entity.getDurationMs())
                .exitMessage(entity.getExitMessage())
                .errorDetails(entity.getErrorDetails())
                .build();
    }
}
