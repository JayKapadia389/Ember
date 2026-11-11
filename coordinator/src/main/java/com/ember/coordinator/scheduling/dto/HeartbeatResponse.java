package com.ember.coordinator.scheduling.dto;

/**
 * @param owned false means stop the reported job and discard its result (decision 032)
 */
public record HeartbeatResponse(boolean owned) {}
