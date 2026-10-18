package com.ember.coordinator.common;

public abstract class EmberException extends RuntimeException {
    private final ErrorKind kind;

    protected EmberException (ErrorKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ErrorKind getKind() {
        return kind;
    }
}