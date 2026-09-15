package com.repopilot.security;

/** Hard limits that protect the analyser from hostile or pathological repositories. */
public record ResourceLimits(long maxFileBytes, int maxDirectoryDepth, int maxFiles, long maxTotalBytes) {

    public ResourceLimits {
        if (maxFileBytes <= 0 || maxDirectoryDepth <= 0 || maxFiles <= 0 || maxTotalBytes <= 0) {
            throw new IllegalArgumentException("resource limits must all be positive");
        }
    }

    public static ResourceLimits defaults() {
        return new ResourceLimits(
                2L * 1024 * 1024,   // a 2 MiB Java file is already far beyond anything hand-written
                64,                 // directory recursion guard
                200_000,            // file count guard
                4L * 1024 * 1024 * 1024);
    }

    public ResourceLimits withMaxFileBytes(long bytes) {
        return new ResourceLimits(bytes, maxDirectoryDepth, maxFiles, maxTotalBytes);
    }
}
