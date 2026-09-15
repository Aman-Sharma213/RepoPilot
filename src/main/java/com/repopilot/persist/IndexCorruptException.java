package com.repopilot.persist;

/** Thrown when the on-disk index exists but cannot be trusted. Callers must re-index, not guess. */
public class IndexCorruptException extends RuntimeException {

    public IndexCorruptException(String message, Throwable cause) {
        super(message, cause);
    }

    public IndexCorruptException(String message) {
        super(message);
    }
}
