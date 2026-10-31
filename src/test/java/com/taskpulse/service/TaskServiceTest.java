package com.taskpulse.service;

import com.taskpulse.dto.CreateTaskRequest;
import com.taskpulse.dto.TaskResponse;
import com.taskpulse.entity.TaskEntity;
import com.taskpulse.enums.TaskPriority;
import com.taskpulse.enums.TaskStatus;
import com.taskpulse.enums.TaskType;
import com.taskpulse.repository.TaskExecutionRepository;
import com.taskpulse.repository.TaskRepository;
import com.taskpulse.worker.PriorityWorkerPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskExecutionRepository executionRepository;

    @Mock
    private LeaderElectionService leaderElectionService;

    @Mock
    private PriorityWorkerPool workerPool;

    @Mock
    private TaskExecutorService executorService;

    @InjectMocks
    private TaskService taskService;

    private CreateTaskRequest cronRequest;
    private TaskEntity taskEntity;

    @BeforeEach
    void setUp() {
        cronRequest = CreateTaskRequest.builder()
                .name("nightly-reconciliation")
                .description("Cleans up pending records")
                .type(TaskType.CRON)
                .cronExpression("0 0 2 * * *")
                .priority(TaskPriority.HIGH)
                .maxRetries(3)
                .timeoutSeconds(120)
                .build();

        taskEntity = TaskEntity.builder()
                .id(UUID.randomUUID())
                .name("nightly-reconciliation")
                .type(TaskType.CRON)
                .cronExpression("0 0 2 * * *")
                .priority(TaskPriority.HIGH)
                .status(TaskStatus.PENDING)
                .maxRetries(3)
                .timeoutSeconds(120)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should successfully schedule a valid CRON task and calculate next run")
    void shouldCreateCronTaskSuccessfully() {
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(i -> {
            TaskEntity entity = i.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        TaskResponse response = taskService.createTask(cronRequest);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("nightly-reconciliation");
        assertThat(response.getType()).isEqualTo(TaskType.CRON);
        assertThat(response.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(response.getNextRunAt()).isNotNull();
        assertThat(response.getNextRunAt()).isAfter(Instant.now());

        verify(taskRepository, times(1)).save(any(TaskEntity.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when cron expression is invalid")
    void shouldRejectInvalidCronExpression() {
        cronRequest.setCronExpression("invalid * * cron *");

        assertThatThrownBy(() -> taskService.createTask(cronRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Cron expression");

        verify(taskRepository, never()).save(any(TaskEntity.class));
    }

    @Test
    @DisplayName("Should pause a scheduled task")
    void shouldPauseTask() {
        UUID id = taskEntity.getId();
        when(taskRepository.findById(id)).thenReturn(Optional.of(taskEntity));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(i -> i.getArgument(0));

        TaskResponse response = taskService.pauseTask(id);

        assertThat(response.getStatus()).isEqualTo(TaskStatus.PAUSED);
        verify(taskRepository).save(taskEntity);
    }

    @Test
    @DisplayName("Should resume a paused task")
    void shouldResumeTask() {
        taskEntity.setStatus(TaskStatus.PAUSED);
        UUID id = taskEntity.getId();
        when(taskRepository.findById(id)).thenReturn(Optional.of(taskEntity));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(i -> i.getArgument(0));

        TaskResponse response = taskService.resumeTask(id);

        assertThat(response.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(response.getNextRunAt()).isNotNull();
        verify(taskRepository).save(taskEntity);
    }

    @Test
    @DisplayName("Should trigger manual execution immediately and submit to worker pool")
    void shouldTriggerTaskImmediately() {
        UUID id = taskEntity.getId();
        when(taskRepository.findById(id)).thenReturn(Optional.of(taskEntity));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(i -> i.getArgument(0));

        TaskResponse response = taskService.triggerTaskImmediately(id);

        assertThat(response.getStatus()).isEqualTo(TaskStatus.PENDING);
        verify(workerPool, times(1)).submit(eq(taskEntity), any(Runnable.class));
    }
}
