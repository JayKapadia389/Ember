package com.ember.coordinator.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param sweepInterval how often the dead-worker sweep runs
 * @param workerTimeout silence after which a worker counts as dead (decision 032)
 * @param orphanGrace   how long a fresh assignment is safe from orphan requeue (decision 033)
 */
@ConfigurationProperties("ember.scheduling")
record SchedulingProperties(Duration sweepInterval, Duration workerTimeout, Duration orphanGrace) {}
