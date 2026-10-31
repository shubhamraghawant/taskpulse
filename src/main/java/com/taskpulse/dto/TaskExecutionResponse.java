package com.taskpulse.dto;

import com.taskpulse.enums.ExecutionStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskExecutionResponse {
    private UUID id;
    private UUID taskId;
    private String nodeInstanceId;
    private int attemptNumber;
    private ExecutionStatus status;
    private Instant startedAt;
    private Instant finishedAt;
    private Long durationMs;
    private String exitMessage;
    private String errorDetails;
}
