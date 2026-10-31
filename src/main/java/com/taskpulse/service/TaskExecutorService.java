package com.taskpulse.service;

import com.taskpulse.entity.TaskEntity;
import com.taskpulse.entity.TaskExecutionEntity;
import com.taskpulse.enums.ExecutionStatus;
import com.taskpulse.enums.TaskStatus;
import com.taskpulse.enums.TaskType;
import com.taskpulse.repository.TaskExecutionRepository;
import com.taskpulse.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutorService {

    private final TaskRepository taskRepository;
    private final TaskExecutionRepository executionRepository;
    private final LeaderElectionService leaderElectionService;

    @Transactional
    public void executeTask(UUID taskId) {
        Optional<TaskEntity> taskOpt = taskRepository.findByIdWithLock(taskId);
        if (taskOpt.isEmpty()) {
            log.warn("Task [{}] not found for execution", taskId);
            return;
        }

        TaskEntity task = taskOpt.get();

        // Ensure task is still in executable state
        if (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.RETRYING) {
            log.debug("Skipping task [{}] because its status is {}", taskId, task.getStatus());
            return;
        }

        task.setStatus(TaskStatus.RUNNING);
        task.setLastRunAt(Instant.now());
        task = taskRepository.save(task);

        Instant startedAt = Instant.now();
        int attempt = task.getCurrentRetryCount() + 1;
        String nodeId = leaderElectionService.getNodeId();

        boolean success = false;
        String exitMessage = "Executed successfully";
        String errorDetails = null;

        try {
            log.info("▶️ [Node {}] Starting execution of task: '{}' (ID: {}, Attempt: {})",
                    nodeId, task.getName(), task.getId(), attempt);

            // Execute task logic (simulate realistic work based on payload)
            performTaskWork(task);
            success = true;

        } catch (Exception e) {
            success = false;
            exitMessage = "Execution failed: " + e.getMessage();
            errorDetails = getStackTraceAsString(e);
            log.error("❌ Task [{}] execution failed: {}", task.getId(), e.getMessage());
        }

        Instant finishedAt = Instant.now();
        long durationMs = Duration.between(startedAt, finishedAt).toMillis();

        // Record execution audit log
        TaskExecutionEntity execution = TaskExecutionEntity.builder()
                .taskId(task.getId())
                .nodeInstanceId(nodeId)
                .attemptNumber(attempt)
                .status(success ? ExecutionStatus.SUCCESS : (attempt >= task.getMaxRetries() ? ExecutionStatus.FAILED : ExecutionStatus.RETRYING))
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .durationMs(durationMs)
                .exitMessage(exitMessage)
                .errorDetails(errorDetails)
                .build();
        executionRepository.save(execution);

        // Update task state machine
        if (success) {
            handleTaskSuccess(task, finishedAt);
        } else {
            handleTaskFailure(task, attempt);
        }

        taskRepository.save(task);
    }

    private void performTaskWork(TaskEntity task) throws Exception {
        String payload = task.getPayload();
        // Allow controlled test simulation of failure via payload
        if (payload != null && payload.contains("\"simulateFailure\": true")) {
            throw new RuntimeException("Simulated upstream worker failure for resilience testing");
        }

        // Simulate typical worker I/O or computation
        Thread.sleep(80);
    }

    private void handleTaskSuccess(TaskEntity task, Instant finishedAt) {
        task.setCurrentRetryCount(0);

        if (task.getType() == TaskType.CRON && task.getCronExpression() != null) {
            CronExpression cron = CronExpression.parse(task.getCronExpression());
            ZonedDateTime nextZoned = cron.next(finishedAt.atZone(ZoneId.systemDefault()));
            if (nextZoned != null) {
                task.setNextRunAt(nextZoned.toInstant());
                task.setStatus(TaskStatus.PENDING);
                log.info("Scheduled next cron run for '{}' at {}", task.getName(), task.getNextRunAt());
            } else {
                task.setStatus(TaskStatus.COMPLETED);
            }
        } else {
            task.setStatus(TaskStatus.COMPLETED);
            task.setNextRunAt(null);
            log.info("Task '{}' successfully completed.", task.getName());
        }
    }

    private void handleTaskFailure(TaskEntity task, int attempt) {
        task.setCurrentRetryCount(attempt);

        if (attempt >= task.getMaxRetries()) {
            task.setStatus(TaskStatus.DEAD_LETTER);
            task.setNextRunAt(null);
            log.warn("⚠️ Task '{}' exceeded max retries ({}). Moved to DEAD_LETTER queue.",
                    task.getName(), task.getMaxRetries());
        } else {
            // Exponential backoff: baseDelay * 2^(attempt - 1), e.g. 5s, 10s, 20s
            long backoffSeconds = Math.min(300, (long) (5 * Math.pow(2, attempt - 1)));
            Instant nextRetry = Instant.now().plusSeconds(backoffSeconds);
            task.setNextRunAt(nextRetry);
            task.setStatus(TaskStatus.RETRYING);
            log.info("Task '{}' scheduled for retry #{} in {} seconds (at {})",
                    task.getName(), attempt + 1, backoffSeconds, nextRetry);
        }
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append(element.toString()).append("\n");
            if (sb.length() > 2000) {
                sb.append("... [truncated]");
                break;
            }
        }
        return sb.toString();
    }
}
