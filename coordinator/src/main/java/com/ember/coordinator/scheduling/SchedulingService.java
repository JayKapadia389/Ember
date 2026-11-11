package com.ember.coordinator.scheduling;

import com.ember.coordinator.job.ClaimedJob;
import com.ember.coordinator.job.JobService;
import com.ember.coordinator.worker.WorkerService;
import com.ember.coordinator.worker.WorkerSpecs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final WorkerService workerService;
    private final JobService jobService;
    private final SchedulingProperties properties;

    // One transaction: the claim reads the worker and updates jobs together (decision 019).
    @Transactional
    public Optional<ClaimedJob> poll(UUID workerId) {
        WorkerSpecs specs = workerService.recordSeen(workerId);
        return jobService.claimNext(workerId, specs.vramMb());
    }

    /**
     * @param runningJobId the job the worker reports running, or null if none
     * @return whether the worker still owns {@code runningJobId}; false if none was reported
     */
    @Transactional
    public boolean heartbeat(UUID workerId, UUID runningJobId) {
        workerService.recordSeen(workerId);
        boolean owned = runningJobId != null && jobService.confirmOwnership(runningJobId, workerId);
        int requeued = jobService.requeueOrphans(workerId, runningJobId, properties.orphanGrace());
        if (requeued > 0) {
            log.info("Requeued {} job(s) assigned to worker {} that it isn't running", requeued, workerId);
        }
        return owned;
    }
}
