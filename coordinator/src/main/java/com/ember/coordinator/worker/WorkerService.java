package com.ember.coordinator.worker;

import com.ember.coordinator.worker.dto.WorkerRegistrationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
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

    @Transactional
    public boolean deregister(UUID id) {
        if (!workerRepository.existsById(id)) return false;
        workerRepository.deleteById(id);
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<WorkerSpecs> getSpecs(UUID id) {
        return workerRepository.findSpecsById(id);
    }
}