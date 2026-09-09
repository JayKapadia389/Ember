package com.ember.coordinator.controllers;

import com.ember.coordinator.dtos.PingResponseDto;
import com.ember.coordinator.dtos.WorkerRegistrationDto;
import com.ember.coordinator.services.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/worker")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    @PostMapping("/register")
    UUID registerWorker(@RequestBody WorkerRegistrationDto workerRegistrationDto) {
        return workerService.registerWorker(workerRegistrationDto);
    }

    @GetMapping("/ping/{workerId}")
    PingResponseDto ping(@PathVariable UUID workerId) {
        return new PingResponseDto();
    }
}
