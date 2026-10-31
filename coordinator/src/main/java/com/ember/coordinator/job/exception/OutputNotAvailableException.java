package com.ember.coordinator.job.exception;

import com.ember.coordinator.common.EmberException;
import com.ember.coordinator.common.ErrorKind;

import java.util.UUID;

public class OutputNotAvailableException extends EmberException {

    public OutputNotAvailableException(UUID jobId) {
        super(ErrorKind.NOT_FOUND, "Job " + jobId + " has no output");
    }
}
