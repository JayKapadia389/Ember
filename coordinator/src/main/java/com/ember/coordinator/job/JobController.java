package com.ember.coordinator.job;

import com.ember.coordinator.job.dto.JobCreateRequest;
import com.ember.coordinator.job.dto.JobCreatedResponse;
import com.ember.coordinator.job.dto.JobResponse;
import com.ember.coordinator.job.dto.ResultOutcome;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<JobCreatedResponse> create(@Valid @RequestBody JobCreateRequest request) {
        UUID id = jobService.create(request);
        return ResponseEntity
                .created(URI.create("/jobs/" + id))
                .body(new JobCreatedResponse(id));
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable UUID id) {
        return jobService.get(id);
    }

    @GetMapping("/{id}/output")
    public ResponseEntity<Resource> output(@PathVariable UUID id) {
        JobOutput output = jobService.getOutput(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(output.contentType()))
                .body(output.content());
    }

    @PostMapping(value = "/{id}/result", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> submitResult(
            @PathVariable UUID id,
            @RequestHeader("X-Worker-Id") UUID workerId,
            @RequestParam ResultOutcome outcome,
            @RequestParam(required = false) String error,
            @RequestPart(required = false) MultipartFile output) {
        jobService.submitResult(id, workerId, outcome, error, output);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<Void> release(@PathVariable UUID id, @RequestHeader("X-Worker-Id") UUID workerId) {
        jobService.release(id, workerId);
        return ResponseEntity.noContent().build();
    }
}
