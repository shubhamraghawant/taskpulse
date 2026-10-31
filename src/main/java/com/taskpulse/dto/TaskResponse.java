package com.taskpulse.dto;

import com.taskpulse.enums.TaskPriority;
import com.taskpulse.enums.TaskStatus;
import com.taskpulse.enums.TaskType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskResponse {
    private UUID id;
    private String name;
    private String description;
    private TaskType type;
    private String cronExpression;
    private TaskPriority priority;
    private TaskStatus status;
    private String payload;
    private int maxRetries;
    private int currentRetryCount;
    private long timeoutSeconds;
    private Instant nextRunAt;
    private Instant lastRunAt;
    private Instant createdAt;
    private Instant updatedAt;
}
