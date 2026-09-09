package com.ember.coordinator.exceptions;

import com.ember.coordinator.errors.ErrorConstants;

import java.util.UUID;

public class NoSuchTaskException extends RuntimeException {
    public NoSuchTaskException(UUID taskId) {
        super(String.format(ErrorConstants.NO_SUCH_TASK_FOUND, taskId));
    }
}
