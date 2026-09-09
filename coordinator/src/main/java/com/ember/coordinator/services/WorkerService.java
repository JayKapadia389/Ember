package com.ember.coordinator.services;

import com.ember.coordinator.dtos.WorkerRegistrationDto;
import com.ember.coordinator.entities.Worker;
import com.ember.coordinator.repositories.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkerService {

    private final WorkerRepository workerRepository;

    public UUID registerWorker(WorkerRegistrationDto workerRegistrationDto) {
        Worker worker = new Worker();
        worker.setVram(workerRegistrationDto.vram());
        workerRepository.save(worker);
        Worker saved = workerRepository.save(worker);
        return saved.getId();
    }
}
