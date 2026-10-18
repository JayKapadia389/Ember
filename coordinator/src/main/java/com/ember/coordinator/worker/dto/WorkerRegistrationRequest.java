package com.ember.coordinator.worker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record WorkerRegistrationRequest(
        @NotNull @Positive Integer vramMb,
        @Size(max = 255) String gpuName
) {}
