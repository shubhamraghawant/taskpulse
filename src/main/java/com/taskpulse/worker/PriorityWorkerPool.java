package com.taskpulse.worker;

import com.taskpulse.entity.TaskEntity;
import com.taskpulse.enums.TaskPriority;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class PriorityWorkerPool {

    private final ThreadPoolExecutor executor;
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);

    public PriorityWorkerPool(
            @Value("${taskpulse.workers.core-pool-size:8}") int corePoolSize,
            @Value("${taskpulse.workers.max-pool-size:16}") int maxPoolSize,
            @Value("${taskpulse.workers.queue-capacity:1000}") int queueCapacity) {

        BlockingQueue<Runnable> priorityQueue = new PriorityBlockingQueue<>(queueCapacity);

        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L,
                TimeUnit.SECONDS,
                priorityQueue,
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "taskpulse-worker-" + counter.getAndIncrement());
                        thread.setDaemon(false);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("Initialized PriorityWorkerPool with corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                corePoolSize, maxPoolSize, queueCapacity);
    }

    public void submit(TaskEntity task, Runnable taskAction) {
        executor.execute(new PrioritizedTaskRunnable(task, () -> {
            activeTaskCount.incrementAndGet();
            try {
                taskAction.run();
            } finally {
                activeTaskCount.decrementAndGet();
            }
        }));
    }

    public int getActiveWorkerCount() {
        return activeTaskCount.get();
    }

    public int getQueueDepth() {
        return executor.getQueue().size();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Initiating graceful shutdown of PriorityWorkerPool...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("PriorityWorkerPool did not terminate within 30 seconds. Forcing shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Shutdown interrupted: {}", e.getMessage());
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Getter
    public static class PrioritizedTaskRunnable implements Runnable, Comparable<PrioritizedTaskRunnable> {
        private final TaskPriority priority;
        private final Instant nextRunAt;
        private final String taskName;
        private final Runnable delegate;

        public PrioritizedTaskRunnable(TaskEntity task, Runnable delegate) {
            this.priority = task.getPriority() != null ? task.getPriority() : TaskPriority.NORMAL;
            this.nextRunAt = task.getNextRunAt() != null ? task.getNextRunAt() : Instant.now();
            this.taskName = task.getName();
            this.delegate = delegate;
        }

        @Override
        public void run() {
            delegate.run();
        }

        @Override
        public int compareTo(PrioritizedTaskRunnable other) {
            // Lower rank number means higher priority (CRITICAL=1, HIGH=2, NORMAL=3, LOW=4)
            int priorityCompare = Integer.compare(this.priority.getRank(), other.priority.getRank());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return this.nextRunAt.compareTo(other.nextRunAt);
        }
    }
}
