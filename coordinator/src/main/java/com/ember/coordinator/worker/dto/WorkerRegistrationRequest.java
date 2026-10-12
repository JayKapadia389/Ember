package com.ember.coordinator.worker.dto;

public record WorkerRegistrationRequest(int vramMb, String gpuName) {}