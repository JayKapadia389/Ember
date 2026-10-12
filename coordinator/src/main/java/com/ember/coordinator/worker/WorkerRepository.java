package com.ember.coordinator.worker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface WorkerRepository extends JpaRepository<Worker, UUID> {

    Optional<WorkerSpecs> findSpecsById(UUID id);

}