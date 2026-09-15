package com.repopilot.scan;

/**
 * A path the scanner deliberately skipped, together with the reason. Exposing the reason keeps the
 * scan explainable: users can always find out why a file is missing from the index.
 */
public record IgnoredFile(String relativePath, IgnoreReason reason, String detail) {

    public enum IgnoreReason {
        IGNORED_DIRECTORY,
        IGNORED_NAME,
        GENERATED,
        BINARY,
        NOT_JAVA,
        SYMLINK,
        TOO_LARGE,
        UNREADABLE,
        LIMIT_EXCEEDED
    }
}
