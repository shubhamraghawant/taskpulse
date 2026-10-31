package com.taskpulse.controller;

import com.taskpulse.dto.TaskExecutionResponse;
import com.taskpulse.enums.ExecutionStatus;
import com.taskpulse.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
@Tag(name = "Executions", description = "Audit trail and execution history APIs")
public class TaskExecutionController {

    private final TaskService taskService;

    @Operation(summary = "List all task executions", description = "Returns global execution audit logs with status filter")
    @GetMapping
    public ResponseEntity<Page<TaskExecutionResponse>> listAllExecutions(
            @RequestParam(required = false) ExecutionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        return ResponseEntity.ok(taskService.listAllExecutions(status, pageRequest));
    }

    @Operation(summary = "Get task execution history", description = "Retrieves execution history specifically for one task ID")
    @GetMapping("/task/{taskId}")
    public ResponseEntity<Page<TaskExecutionResponse>> getExecutionsForTask(
            @Parameter(description = "Task UUID") @PathVariable UUID taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        return ResponseEntity.ok(taskService.getExecutionsForTask(taskId, pageRequest));
    }
}
