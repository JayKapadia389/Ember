package com.ember.coordinator.common;

import java.net.URI;

public abstract class EmberException extends RuntimeException {
    private final ErrorKind kind;
    private final URI type;

    protected EmberException (ErrorKind kind, String message) {
        this(kind, null, message);
    }

    /**
     * @param type ProblemDetail type for errors a client must tell apart from others
     *             with the same status, e.g. unknown worker vs. plain 404. May be null.
     */
    protected EmberException (ErrorKind kind, URI type, String message) {
        super(message);
        this.kind = kind;
        this.type = type;
    }

    public ErrorKind getKind() {
        return kind;
    }

    public URI getType() {
        return type;
    }
}
