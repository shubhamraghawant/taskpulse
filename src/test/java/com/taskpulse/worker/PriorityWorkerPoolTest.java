package com.taskpulse.worker;

import com.taskpulse.entity.TaskEntity;
import com.taskpulse.enums.TaskPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityWorkerPoolTest {

    @Test
    @DisplayName("PrioritizedTaskRunnable should order CRITICAL before NORMAL and LOW")
    void shouldPrioritizeHigherRankTasks() {
        TaskEntity criticalTask = TaskEntity.builder()
                .name("critical-job")
                .priority(TaskPriority.CRITICAL)
                .nextRunAt(Instant.now().plusSeconds(60))
                .build();

        TaskEntity normalTask = TaskEntity.builder()
                .name("normal-job")
                .priority(TaskPriority.NORMAL)
                .nextRunAt(Instant.now())
                .build();

        TaskEntity lowTask = TaskEntity.builder()
                .name("low-job")
                .priority(TaskPriority.LOW)
                .nextRunAt(Instant.now())
                .build();

        var criticalRunnable = new PriorityWorkerPool.PrioritizedTaskRunnable(criticalTask, () -> {});
        var normalRunnable = new PriorityWorkerPool.PrioritizedTaskRunnable(normalTask, () -> {});
        var lowRunnable = new PriorityWorkerPool.PrioritizedTaskRunnable(lowTask, () -> {});

        // compareTo returns negative if 'this' is higher priority than 'other'
        assertThat(criticalRunnable.compareTo(normalRunnable)).isNegative();
        assertThat(normalRunnable.compareTo(lowRunnable)).isNegative();
        assertThat(lowRunnable.compareTo(criticalRunnable)).isPositive();
    }
}
