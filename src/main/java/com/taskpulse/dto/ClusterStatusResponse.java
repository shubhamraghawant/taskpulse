package com.taskpulse.dto;

import lombok.*;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClusterStatusResponse {
    private String nodeId;
    private boolean leader;
    private String currentLeaderNode;
    private Instant leaderLeaseExpiresAt;
    private int activeWorkerThreads;
    private int queueDepth;
    private Map<String, Long> taskStatusCounts;
    private Map<String, Long> executionStatusCounts;
    private Instant timestamp;
}
