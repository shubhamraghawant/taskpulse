package com.taskpulse.repository;

import com.taskpulse.entity.LeaderLeaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface LeaderLeaseRepository extends JpaRepository<LeaderLeaseEntity, String> {

    @Modifying
    @Query("UPDATE LeaderLeaseEntity l SET l.lockedByNode = :nodeId, l.expiresAt = :newExpiry, l.acquiredAt = :now " +
           "WHERE l.leaseKey = :leaseKey AND (l.expiresAt < :now OR l.lockedByNode = :nodeId)")
    int tryAcquireOrRenewLease(
            @Param("leaseKey") String leaseKey,
            @Param("nodeId") String nodeId,
            @Param("now") Instant now,
            @Param("newExpiry") Instant newExpiry
    );

    @Modifying
    @Query("UPDATE LeaderLeaseEntity l SET l.expiresAt = :now WHERE l.leaseKey = :leaseKey AND l.lockedByNode = :nodeId")
    int releaseLease(
            @Param("leaseKey") String leaseKey,
            @Param("nodeId") String nodeId,
            @Param("now") Instant now
    );
}
