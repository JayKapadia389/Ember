package com.ember.coordinator.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * {@code params} is checked against the model's JSON Schema in the service (decision 037).
 */
public record JobCreateRequest(
        @NotBlank String modelId,
        @NotNull Map<String, Object> params
) {}
