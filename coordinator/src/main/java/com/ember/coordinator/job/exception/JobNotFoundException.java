package com.ember.coordinator.job.exception;

import com.ember.coordinator.common.EmberException;
import com.ember.coordinator.common.ErrorKind;

import java.util.UUID;

public class JobNotFoundException extends EmberException {

    public JobNotFoundException(UUID jobId) {
        super(ErrorKind.NOT_FOUND, "Job not found: " + jobId);
    }
}
