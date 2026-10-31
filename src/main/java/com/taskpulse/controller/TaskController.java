package com.taskpulse.controller;

import com.taskpulse.dto.CreateTaskRequest;
import com.taskpulse.dto.TaskResponse;
import com.taskpulse.enums.TaskPriority;
import com.taskpulse.enums.TaskStatus;
import com.taskpulse.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Task definition, scheduling, and lifecycle operations")
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "Schedule a new task", description = "Registers a new CRON, DELAYED, or ONE_OFF task definition")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task scheduled successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or cron expression")
    })
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse response = taskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get task by ID", description = "Retrieves details of a specific task definition")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task found"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTask(@Parameter(description = "Task UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(taskService.getTask(id));
    }

    @Operation(summary = "List all tasks", description = "Returns a paginated list of tasks filtered by status and priority")
    @ApiResponse(responseCode = "200", description = "Tasks retrieved successfully")
    @GetMapping
    public ResponseEntity<Page<TaskResponse>> listTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sortBy));
        return ResponseEntity.ok(taskService.listTasks(status, priority, pageRequest));
    }

    @Operation(summary = "Trigger immediate execution", description = "Forces an immediate execution of a task regardless of schedule")
    @PostMapping("/{id}/trigger")
    public ResponseEntity<TaskResponse> triggerTask(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.triggerTaskImmediately(id));
    }

    @Operation(summary = "Pause task schedule", description = "Transitions task to PAUSED state to prevent scheduled executions")
    @PatchMapping("/{id}/pause")
    public ResponseEntity<TaskResponse> pauseTask(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.pauseTask(id));
    }

    @Operation(summary = "Resume task schedule", description = "Reactivates a PAUSED task back to PENDING state")
    @PatchMapping("/{id}/resume")
    public ResponseEntity<TaskResponse> resumeTask(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.resumeTask(id));
    }

    @Operation(summary = "Delete task", description = "Permanently removes a task definition")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
