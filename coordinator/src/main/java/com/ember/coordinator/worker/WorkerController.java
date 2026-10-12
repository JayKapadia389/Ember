package com.ember.coordinator.worker;

import com.ember.coordinator.worker.dto.WorkerRegistrationRequest;
import com.ember.coordinator.worker.dto.WorkerRegistrationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ResponseEntity<WorkerRegistrationResponse> register(@RequestBody WorkerRegistrationRequest request) {
        UUID id = workerService.register(request);
        return ResponseEntity
                .created(URI.create("/workers/" + id))
                .body(new WorkerRegistrationResponse(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deregister(@PathVariable UUID id) {
        return workerService.deregister(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}