package com.repopilot.scan;

import java.nio.file.Path;

/**
 * One file discovered by the scanner.
 *
 * @param relativePath repository-relative path, always using '/' separators
 * @param absolutePath absolute path on disk
 * @param sizeBytes    file size in bytes
 * @param lastModified epoch millis of last modification
 * @param java         true when the file is a Java source file that should be parsed
 */
public record ScannedFile(String relativePath, Path absolutePath, long sizeBytes, long lastModified, boolean java) {
}
