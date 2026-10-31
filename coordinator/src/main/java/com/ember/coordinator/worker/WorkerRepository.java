package com.ember.coordinator.worker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface WorkerRepository extends JpaRepository<Worker, UUID> {

    Optional<WorkerSpecs> findSpecsById(UUID id);

    @Modifying
    @Query(value = "UPDATE workers SET last_seen_at = now() WHERE id = :id", nativeQuery = true)
    int touch(UUID id);

    // Compared against the database clock, the same one touch() writes with.
    @Query(value = """
            SELECT id FROM workers
            WHERE id IN (:ids) AND last_seen_at < now() - make_interval(secs => :silentSeconds)
            """, nativeQuery = true)
    List<UUID> findSilent(Collection<UUID> ids, long silentSeconds);
}
