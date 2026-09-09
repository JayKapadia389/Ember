package com.ember.coordinator.controllers;

import com.ember.coordinator.dtos.WorkerRegistrationDto;
import com.ember.coordinator.services.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
