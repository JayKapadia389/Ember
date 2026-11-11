package com.ember.coordinator.job.exception;

import com.ember.coordinator.common.EmberException;
import com.ember.coordinator.common.ErrorKind;

import java.util.List;

public class InvalidParamsException extends EmberException {

    public InvalidParamsException(List<String> errors) {
        super(ErrorKind.INVALID, "Invalid params: " + String.join("; ", errors));
    }
}
