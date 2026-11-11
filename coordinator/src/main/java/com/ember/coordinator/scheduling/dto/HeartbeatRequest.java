package com.ember.coordinator.scheduling.dto;

import java.util.UUID;

/**
 * @param jobId the job the worker is running, or null if idle
 */
public record HeartbeatRequest(UUID jobId) {}
