package com.taskpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "leader_leases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaderLeaseEntity {

    @Id
    @Column(name = "lease_key", length = 50)
    private String leaseKey;

    @Column(name = "locked_by_node", nullable = false, length = 100)
    private String lockedByNode;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
