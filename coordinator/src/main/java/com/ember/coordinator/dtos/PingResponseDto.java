package com.ember.coordinator.dtos;

public record PingResponseDto(String message) {
    public PingResponseDto() {
        this("No jobs currently available matching your capabilities");
    }
}
