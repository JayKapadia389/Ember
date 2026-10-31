package com.ember.coordinator.worker;

import com.ember.coordinator.worker.dto.WorkerRegistrationRequest;
import com.ember.coordinator.worker.exception.UnknownWorkerException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkerService {

    private final WorkerRepository workerRepository;

    public UUID register(WorkerRegistrationRequest workerRegistrationRequest) {
        Worker worker = new Worker();
        worker.setVramMb(workerRegistrationRequest.vramMb());
        worker.setGpuName(workerRegistrationRequest.gpuName());
        Worker savedWorker = workerRepository.save(worker);
        return savedWorker.getId();
    }

    @Transactional(readOnly = true)
    public Optional<WorkerSpecs> getSpecs(UUID id) {
        return workerRepository.findSpecsById(id);
    }

    /**
     * Marks the worker alive. Every worker-facing request calls this first,
     * so each one counts as a sign of life and rejects unknown IDs the same way.
     */
    @Transactional
    public WorkerSpecs recordSeen(UUID id) {
        if (workerRepository.touch(id) == 0) {
            throw new UnknownWorkerException(id);
        }
        return workerRepository.findSpecsById(id).orElseThrow(() -> new UnknownWorkerException(id));
    }

    /**
     * Returns those of the given workers not seen within {@code silence}.
     */
    @Transactional(readOnly = true)
    public Set<UUID> filterSilent(Collection<UUID> ids, Duration silence) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(workerRepository.findSilent(ids, silence.toSeconds()));
    }
}
