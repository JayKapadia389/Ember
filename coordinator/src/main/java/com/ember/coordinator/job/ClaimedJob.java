package com.ember.coordinator.job;

import java.util.Map;
import java.util.UUID;

public record ClaimedJob(UUID jobId, String modelId, Map<String, Object> params) {}
