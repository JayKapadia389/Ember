package com.ember.coordinator.worker.exception;

import com.ember.coordinator.common.EmberException;
import com.ember.coordinator.common.ErrorKind;

import java.net.URI;
import java.util.UUID;

/**
 * The worker deletes its saved ID only on this type, never on a plain 404 (decision 035).
 */
public class UnknownWorkerException extends EmberException {

    public static final URI TYPE = URI.create("urn:ember:unknown-worker");

    public UnknownWorkerException(UUID workerId) {
        super(ErrorKind.NOT_FOUND, TYPE, "Unknown worker: " + workerId);
    }
}
