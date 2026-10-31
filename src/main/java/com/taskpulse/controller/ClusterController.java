package com.taskpulse.controller;

import com.taskpulse.dto.ClusterStatusResponse;
import com.taskpulse.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cluster")
@RequiredArgsConstructor
@Tag(name = "Cluster", description = "Cluster coordination, leader election, and worker pool metrics")
public class ClusterController {

    private final TaskService taskService;

    @Operation(summary = "Get cluster status & metrics", description = "Returns leader election status, worker thread pool utilization, and task state distribution")
    @GetMapping("/status")
    public ResponseEntity<ClusterStatusResponse> getClusterStatus() {
        return ResponseEntity.ok(taskService.getClusterStatus());
    }
}
