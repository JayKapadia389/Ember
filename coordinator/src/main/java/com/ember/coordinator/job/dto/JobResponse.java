package com.ember.coordinator.job.dto;

import com.ember.coordinator.job.JobStatus;
import com.ember.coordinator.job.JobType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * @param outputUrl set once the job is COMPLETED
 * @param error     set once the job is FAILED
 */
public record JobResponse(
        UUID id,
        String modelId,
        JobType type,
        JobStatus status,
        Map<String, Object> params,
        Instant createdAt,
        Instant finishedAt,
        String outputUrl,
        String error
) {}
