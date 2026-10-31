package com.taskpulse.entity;

import com.taskpulse.enums.ExecutionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_executions", indexes = {
        @Index(name = "idx_executions_task_id", columnList = "task_id"),
        @Index(name = "idx_executions_started_at", columnList = "started_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "node_instance_id", nullable = false, length = 100)
    private String nodeInstanceId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "exit_message", length = 1000)
    private String exitMessage;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;
}
