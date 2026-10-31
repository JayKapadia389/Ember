package com.ember.coordinator.job.exception;

/**
 * Unchecked so a failed write rolls back the result transaction. Not an
 * EmberException: it is our fault, so it surfaces as a 500.
 */
public class ArtifactStorageException extends RuntimeException {

    public ArtifactStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
