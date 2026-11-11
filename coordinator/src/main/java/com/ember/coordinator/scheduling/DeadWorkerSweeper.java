package com.ember.coordinator.scheduling;

import com.ember.coordinator.job.JobService;
import com.ember.coordinator.worker.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Fallback for workers that vanish without reporting (decision 032). Starts from
 * assigned jobs, not workers: running jobs are few, while dead worker rows are
 * never deleted and only grow.
 *
 * A worker that comes back after its job was requeued gets it back through the
 * heartbeat ownership check, if no one else has claimed it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DeadWorkerSweeper {

    private final WorkerService workerService;
    private final JobService jobService;
    private final SchedulingProperties properties;

    @Scheduled(fixedDelayString = "${ember.scheduling.sweep-interval}")
    void sweep() {
        Set<UUID> assigned = jobService.assignedWorkerIds();
        Set<UUID> dead = workerService.filterSilent(assigned, properties.workerTimeout());
        if (dead.isEmpty()) {
            return;
        }
        int requeued = jobService.requeueJobsOf(dead);
        log.info("Requeued {} job(s) from {} silent worker(s)", requeued, dead.size());
    }
}
