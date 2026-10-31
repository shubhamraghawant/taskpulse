package com.taskpulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskpulse.config.GlobalExceptionHandler;
import com.taskpulse.dto.ClusterStatusResponse;
import com.taskpulse.dto.CreateTaskRequest;
import com.taskpulse.dto.TaskResponse;
import com.taskpulse.enums.TaskPriority;
import com.taskpulse.enums.TaskStatus;
import com.taskpulse.enums.TaskType;
import com.taskpulse.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {TaskController.class, ClusterController.class})
@Import(GlobalExceptionHandler.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    private UUID sampleTaskId;
    private TaskResponse sampleTaskResponse;

    @BeforeEach
    void setUp() {
        sampleTaskId = UUID.randomUUID();
        sampleTaskResponse = TaskResponse.builder()
                .id(sampleTaskId)
                .name("hourly-sync")
                .description("Syncs user accounts")
                .type(TaskType.CRON)
                .cronExpression("0 0 * * * *")
                .priority(TaskPriority.HIGH)
                .status(TaskStatus.PENDING)
                .maxRetries(3)
                .timeoutSeconds(180)
                .nextRunAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tasks should return 201 Created on valid request")
    void shouldCreateTaskSuccessfully() throws Exception {
        CreateTaskRequest request = CreateTaskRequest.builder()
                .name("hourly-sync")
                .type(TaskType.CRON)
                .cronExpression("0 0 * * * *")
                .priority(TaskPriority.HIGH)
                .build();

        when(taskService.createTask(any(CreateTaskRequest.class))).thenReturn(sampleTaskResponse);

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(sampleTaskId.toString()))
                .andExpect(jsonPath("$.name").value("hourly-sync"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/v1/tasks/{id} should return 200 and task details")
    void shouldGetTaskById() throws Exception {
        when(taskService.getTask(sampleTaskId)).thenReturn(sampleTaskResponse);

        mockMvc.perform(get("/api/v1/tasks/{id}", sampleTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleTaskId.toString()))
                .andExpect(jsonPath("$.name").value("hourly-sync"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    @DisplayName("PATCH /api/v1/tasks/{id}/pause should return 200 and PAUSED status")
    void shouldPauseTask() throws Exception {
        sampleTaskResponse.setStatus(TaskStatus.PAUSED);
        when(taskService.pauseTask(sampleTaskId)).thenReturn(sampleTaskResponse);

        mockMvc.perform(patch("/api/v1/tasks/{id}/pause", sampleTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    @DisplayName("GET /api/v1/cluster/status should return 200 and cluster metrics")
    void shouldGetClusterStatus() throws Exception {
        ClusterStatusResponse clusterStatus = ClusterStatusResponse.builder()
                .nodeId("test-node-01")
                .leader(true)
                .currentLeaderNode("test-node-01")
                .leaderLeaseExpiresAt(Instant.now().plusSeconds(10))
                .activeWorkerThreads(2)
                .queueDepth(0)
                .taskStatusCounts(Map.of("PENDING", 5L, "COMPLETED", 12L))
                .executionStatusCounts(Map.of("SUCCESS", 12L, "FAILED", 0L))
                .timestamp(Instant.now())
                .build();

        when(taskService.getClusterStatus()).thenReturn(clusterStatus);

        mockMvc.perform(get("/api/v1/cluster/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value("test-node-01"))
                .andExpect(jsonPath("$.leader").value(true))
                .andExpect(jsonPath("$.activeWorkerThreads").value(2));
    }
}
