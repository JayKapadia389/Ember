package com.ember.coordinator.job.exception;

import com.ember.coordinator.common.EmberException;
import com.ember.coordinator.common.ErrorKind;

public class UnknownModelException extends EmberException {

    public UnknownModelException(String modelId) {
        super(ErrorKind.INVALID, "Unknown model: " + modelId);
    }
}
