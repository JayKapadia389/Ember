package com.ember.coordinator.job.dto;

/**
 * What a worker reports. Worker faults are not results; they go through release (decision 032).
 */
public enum ResultOutcome {
    COMPLETED,
    FAILED
}
