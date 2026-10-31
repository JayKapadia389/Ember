package com.ember.coordinator.job.exception;

import com.ember.coordinator.common.EmberException;
import com.ember.coordinator.common.ErrorKind;

import java.util.UUID;

public class JobNotOwnedException extends EmberException {

    public JobNotOwnedException(UUID jobId, UUID workerId) {
        super(ErrorKind.CONFLICT, "Job " + jobId + " is not assigned to worker " + workerId);
    }
}
