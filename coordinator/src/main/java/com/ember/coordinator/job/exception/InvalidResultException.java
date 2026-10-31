package com.ember.coordinator.job.exception;

import com.ember.coordinator.common.EmberException;
import com.ember.coordinator.common.ErrorKind;

public class InvalidResultException extends EmberException {

    public InvalidResultException(String message) {
        super(ErrorKind.INVALID, message);
    }
}
