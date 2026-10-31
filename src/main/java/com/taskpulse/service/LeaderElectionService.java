package com.taskpulse.service;

import com.taskpulse.entity.LeaderLeaseEntity;
import com.taskpulse.repository.LeaderLeaseRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderElectionService {

    public static final String LEADER_KEY = "TASK_PULSE_LEADER";
    private static final Duration LEASE_DURATION = Duration.ofSeconds(10);

    private final LeaderLeaseRepository leaseRepository;

    @Getter
    private String nodeId;

    @Getter
    private volatile boolean isLeader = false;

    @Value("${taskpulse.node-id:#{null}}")
    private String configuredNodeId;

    @PostConstruct
    public void init() {
        this.nodeId = (configuredNodeId != null && !configuredNodeId.isBlank())
                ? configuredNodeId
                : "node-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Initialized TaskPulse cluster node with ID: {}", nodeId);
        ensureLeaseRecordExists();
    }

    @Transactional
    public void ensureLeaseRecordExists() {
        if (!leaseRepository.existsById(LEADER_KEY)) {
            try {
                LeaderLeaseEntity initialLease = LeaderLeaseEntity.builder()
                        .leaseKey(LEADER_KEY)
                        .lockedByNode("none")
                        .acquiredAt(Instant.EPOCH)
                        .expiresAt(Instant.EPOCH)
                        .build();
                leaseRepository.save(initialLease);
            } catch (Exception e) {
                log.debug("Lease record already initialized by another node: {}", e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void attemptLeaderLeaseRenewal() {
        Instant now = Instant.now();
        Instant newExpiry = now.plus(LEASE_DURATION);

        try {
            int rowsUpdated = leaseRepository.tryAcquireOrRenewLease(LEADER_KEY, nodeId, now, newExpiry);

            if (rowsUpdated > 0) {
                if (!isLeader) {
                    log.info("🏆 Node [{}] successfully acquired LEADER lease! Starting scheduler operations.", nodeId);
                }
                isLeader = true;
            } else {
                if (isLeader) {
                    log.warn("⚠️ Node [{}] lost leadership lease. Stepping down to FOLLOWER.", nodeId);
                }
                isLeader = false;
            }
        } catch (Exception e) {
            log.error("Error during leader lease negotiation: {}", e.getMessage());
            isLeader = false;
        }
    }

    public Optional<LeaderLeaseEntity> getCurrentLeaseInfo() {
        return leaseRepository.findById(LEADER_KEY);
    }

    @PreDestroy
    @Transactional
    public void releaseLeadershipOnShutdown() {
        if (isLeader) {
            log.info("Shutting down node [{}]. Voluntarily releasing leadership lease.", nodeId);
            leaseRepository.releaseLease(LEADER_KEY, nodeId, Instant.now());
            isLeader = false;
        }
    }
}
