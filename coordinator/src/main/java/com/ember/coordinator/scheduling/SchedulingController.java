package com.ember.coordinator.scheduling;

import com.ember.coordinator.job.ClaimedJob;
import com.ember.coordinator.scheduling.dto.HeartbeatRequest;
import com.ember.coordinator.scheduling.dto.HeartbeatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/scheduling")
@RequiredArgsConstructor
public class SchedulingController {

    private final SchedulingService schedulingService;

    @PostMapping("/poll")
    public ResponseEntity<ClaimedJob> poll(@RequestHeader("X-Worker-Id") UUID workerId) {
        return schedulingService.poll(workerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/heartbeat")
    public HeartbeatResponse heartbeat(@RequestHeader("X-Worker-Id") UUID workerId,
                                       @RequestBody HeartbeatRequest request) {
        return new HeartbeatResponse(schedulingService.heartbeat(workerId, request.jobId()));
    }
}
