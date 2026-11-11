package com.ember.coordinator.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    Optional<Artifact> findByJobIdAndRole(UUID jobId, ArtifactRole role);
}
