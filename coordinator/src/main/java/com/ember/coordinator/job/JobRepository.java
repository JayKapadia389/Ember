package com.ember.coordinator.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every state change is one conditional UPDATE, so concurrent requests can't
 * both act on the same job. Methods returning int report the rows changed.
 */
interface JobRepository extends JpaRepository<Job, UUID> {

    // Decision 012.
    @Query(value = """
            UPDATE jobs SET status = 'ASSIGNED', assigned_worker_id = :workerId, assigned_at = now()
            WHERE id = (SELECT id FROM jobs
                        WHERE status = 'QUEUED' AND model_id IN (:modelIds)
                        ORDER BY created_at LIMIT 1
                        FOR UPDATE SKIP LOCKED)
            RETURNING *
            """, nativeQuery = true)
    Optional<Job> claimNext(UUID workerId, Collection<String> modelIds);

    // Decision 032: still ours, or requeued but not yet taken (handed back).
    @Modifying
    @Query(value = """
            UPDATE jobs SET status = 'ASSIGNED', assigned_worker_id = :workerId,
                            assigned_at = CASE WHEN status = 'QUEUED' THEN now() ELSE assigned_at END
            WHERE id = :jobId
              AND (status = 'QUEUED' OR (status = 'ASSIGNED' AND assigned_worker_id = :workerId))
            """, nativeQuery = true)
    int confirmOwnership(UUID jobId, UUID workerId);

    @Modifying
    @Query(value = """
            UPDATE jobs SET status = 'QUEUED', assigned_worker_id = NULL, assigned_at = NULL
            WHERE id = :jobId AND status = 'ASSIGNED' AND assigned_worker_id = :workerId
            """, nativeQuery = true)
    int release(UUID jobId, UUID workerId);

    // Decision 033: jobs assigned to a worker that says it isn't running them.
    // The grace period skips a job whose poll response is still on its way.
    @Modifying
    @Query(value = """
            UPDATE jobs SET status = 'QUEUED', assigned_worker_id = NULL, assigned_at = NULL
            WHERE status = 'ASSIGNED' AND assigned_worker_id = :workerId
              AND assigned_at < now() - make_interval(secs => :graceSeconds)
            """, nativeQuery = true)
    int requeueOrphans(UUID workerId, long graceSeconds);

    @Modifying
    @Query(value = """
            UPDATE jobs SET status = 'QUEUED', assigned_worker_id = NULL, assigned_at = NULL
            WHERE status = 'ASSIGNED' AND assigned_worker_id = :workerId AND id <> :runningJobId
              AND assigned_at < now() - make_interval(secs => :graceSeconds)
            """, nativeQuery = true)
    int requeueOrphansExcept(UUID workerId, UUID runningJobId, long graceSeconds);

    @Modifying
    @Query(value = """
            UPDATE jobs SET status = 'QUEUED', assigned_worker_id = NULL, assigned_at = NULL
            WHERE status = 'ASSIGNED' AND assigned_worker_id IN (:workerIds)
            """, nativeQuery = true)
    int requeueAssignedTo(Collection<UUID> workerIds);

    @Query(value = "SELECT DISTINCT assigned_worker_id FROM jobs WHERE status = 'ASSIGNED'", nativeQuery = true)
    List<UUID> findAssignedWorkerIds();

    @Modifying
    @Query(value = """
            UPDATE jobs SET status = 'COMPLETED', finished_at = now()
            WHERE id = :jobId AND status = 'ASSIGNED' AND assigned_worker_id = :workerId
            """, nativeQuery = true)
    int complete(UUID jobId, UUID workerId);

    @Modifying
    @Query(value = """
            UPDATE jobs SET status = 'FAILED', finished_at = now(), error_message = :errorMessage
            WHERE id = :jobId AND status = 'ASSIGNED' AND assigned_worker_id = :workerId
            """, nativeQuery = true)
    int fail(UUID jobId, UUID workerId, String errorMessage);
}
