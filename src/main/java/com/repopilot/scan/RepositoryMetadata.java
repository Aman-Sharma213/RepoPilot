package com.repopilot.scan;

/**
 * Summary information about a scan.
 *
 * @param totalFilesSeen  every regular file visited (including skipped ones)
 * @param javaFileCount   Java files accepted for indexing
 * @param ignoredCount    files skipped, for any reason
 * @param totalJavaBytes  accumulated size of accepted Java files
 * @param scanMillis      wall-clock scan duration
 * @param truncated       true when a resource limit stopped the scan early
 */
public record RepositoryMetadata(int totalFilesSeen, int javaFileCount, int ignoredCount,
                                 long totalJavaBytes, long scanMillis, boolean truncated) {
}
