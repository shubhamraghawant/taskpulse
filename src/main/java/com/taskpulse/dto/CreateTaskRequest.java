package com.taskpulse.dto;

import com.taskpulse.enums.TaskPriority;
import com.taskpulse.enums.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTaskRequest {

    @NotBlank(message = "Task name is required")
    @Size(max = 150, message = "Task name must not exceed 150 characters")
    @Schema(example = "nightly-database-reconciliation")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(example = "Reconciles pending billing entries with external payment providers")
    private String description;

    @NotNull(message = "Task type is required (CRON, DELAYED, ONE_OFF)")
    @Schema(example = "CRON")
    private TaskType type;

    @Schema(description = "Standard 6-field Spring Cron expression, e.g. '0 0 2 * * *'", example = "0 */10 * * * *")
    private String cronExpression;

    @Schema(description = "Scheduled execution timestamp for DELAYED or ONE_OFF tasks", example = "2026-11-01T12:00:00Z")
    private Instant scheduledAt;

    @Schema(description = "Priority rank (CRITICAL, HIGH, NORMAL, LOW)", example = "HIGH")
    @Builder.Default
    private TaskPriority priority = TaskPriority.NORMAL;

    @Schema(description = "Arbitrary JSON payload or parameter map for task execution", example = "{\"batchSize\": 500, \"environment\": \"production\"}")
    private String payload;

    @Schema(description = "Maximum retry attempts before DLQ transition", example = "3")
    @Builder.Default
    private int maxRetries = 3;

    @Schema(description = "Execution timeout in seconds", example = "300")
    @Builder.Default
    private long timeoutSeconds = 300;
}
