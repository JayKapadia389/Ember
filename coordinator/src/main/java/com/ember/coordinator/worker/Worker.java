package com.ember.coordinator.worker;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "workers")
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vram_mb", nullable = false)
    private int vramMb;

    @Column(name = "gpu_name")
    private String gpuName;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (registeredAt == null) registeredAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
    }
}