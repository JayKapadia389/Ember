package com.ember.coordinator.worker;

import com.ember.coordinator.worker.dto.WorkerRegistrationRequest;
import com.ember.coordinator.worker.dto.WorkerRegistrationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/workers")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    @PostMapping
    public ResponseEntity<WorkerRegistrationResponse> register(@Valid @RequestBody WorkerRegistrationRequest request) {
        UUID id = workerService.register(request);
        return ResponseEntity
                .created(URI.create("/workers/" + id))
                .body(new WorkerRegistrationResponse(id));
    }
}