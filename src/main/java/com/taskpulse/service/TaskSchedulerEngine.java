package com.taskpulse.service;

import com.taskpulse.entity.TaskEntity;
import com.taskpulse.repository.TaskRepository;
import com.taskpulse.worker.PriorityWorkerPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskSchedulerEngine {

    private final LeaderElectionService leaderElectionService;
    private final TaskRepository taskRepository;
    private final PriorityWorkerPool workerPool;
    private final TaskExecutorService executorService;

    @Scheduled(fixedDelayString = "${taskpulse.scheduler.poll-interval-ms:1000}")
    public void pollAndDispatchDueTasks() {
        if (!leaderElectionService.isLeader()) {
            return;
        }

        try {
            Instant now = Instant.now();
            List<TaskEntity> dueTasks = taskRepository.findDueTasks(now, PageRequest.of(0, 50));

            if (!dueTasks.isEmpty()) {
                log.info("Leader [{}] found {} due tasks ready for execution",
                        leaderElectionService.getNodeId(), dueTasks.size());

                for (TaskEntity task : dueTasks) {
                    workerPool.submit(task, () -> executorService.executeTask(task.getId()));
                }
            }
        } catch (Exception e) {
            log.error("Error during task poll and dispatch: {}", e.getMessage());
        }
    }
}
