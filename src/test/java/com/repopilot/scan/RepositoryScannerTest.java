package com.repopilot.scan;

import com.repopilot.security.ResourceLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryScannerTest {

    /** Builds the synthetic repository from the build plan. */
    private Path syntheticRepo(Path dir) throws IOException {
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/A.java"), "package p; class A {}");
        Files.writeString(dir.resolve("src/B.java"), "package p; class B {}");
        Files.writeString(dir.resolve("src/C.java"), "package p; class C {}");
        Files.writeString(dir.resolve("README.md"), "# test repo");
        Files.createDirectories(dir.resolve("target/classes"));
        Files.writeString(dir.resolve("target/classes/A.java"), "class Stale {}");
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".git/config"), "[core]");
        Files.createDirectories(dir.resolve("node_modules/x"));
        Files.writeString(dir.resolve("node_modules/x/Y.java"), "class Y {}");
        Files.createDirectories(dir.resolve("src/generated-sources"));
        Files.writeString(dir.resolve("src/generated-sources/Gen.java"), "class Gen {}");
        return dir;
    }

    @Test
    void indexesOnlyIntendedFiles(@TempDir Path dir) throws Exception {
        Repository repo = new RepositoryScanner().scan(syntheticRepo(dir));
        assertEquals(List.of("src/A.java", "src/B.java", "src/C.java"),
                repo.javaFiles().stream().map(ScannedFile::relativePath).toList());
        assertEquals(3, repo.metadata().javaFileCount());
        assertFalse(repo.metadata().truncated());
        assertTrue(repo.metadata().totalJavaBytes() > 0);
    }

    @Test
    void resultsAreSortedAndDeterministic(@TempDir Path dir) throws Exception {
        syntheticRepo(dir);
        RepositoryScanner scanner = new RepositoryScanner();
        assertEquals(scanner.scan(dir).javaFiles(), scanner.scan(dir).javaFiles());
    }

    @Test
    void ignoredDirectoriesAreReportedWithReason(@TempDir Path dir) throws Exception {
        Repository repo = new RepositoryScanner().scan(syntheticRepo(dir));
        Set<String> ignoredDirs = repo.ignored().stream()
                .filter(i -> i.reason() == IgnoredFile.IgnoreReason.IGNORED_DIRECTORY)
                .map(IgnoredFile::relativePath).collect(java.util.stream.Collectors.toSet());
        assertTrue(ignoredDirs.containsAll(Set.of(".git", "target", "node_modules")), ignoredDirs.toString());
        assertTrue(repo.ignored().stream().anyMatch(i -> i.relativePath().equals("README.md")
                && i.reason() == IgnoredFile.IgnoreReason.NOT_JAVA));
    }

    @Test
    void generatedSourcesAreSkipped(@TempDir Path dir) throws Exception {
        Repository repo = new RepositoryScanner().scan(syntheticRepo(dir));
        assertTrue(repo.ignored().stream().anyMatch(i -> i.relativePath().equals("src/generated-sources/Gen.java")
                && i.reason() == IgnoredFile.IgnoreReason.GENERATED));
    }

    @Test
    void binaryAndOversizedFilesAreSkipped(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("Blob.java"), new byte[]{1, 2, 0, 3, 4});
        Files.writeString(dir.resolve("Huge.java"), "class Huge {}".repeat(100));
        FileFilter filter = new FileFilter(FileFilter.DEFAULT_IGNORED_DIRECTORIES,
                ResourceLimits.defaults().withMaxFileBytes(64));
        Repository repo = new RepositoryScanner(filter).scan(dir);
        assertTrue(repo.javaFiles().isEmpty());
        assertTrue(repo.ignored().stream().anyMatch(i -> i.reason() == IgnoredFile.IgnoreReason.BINARY));
        assertTrue(repo.ignored().stream().anyMatch(i -> i.reason() == IgnoredFile.IgnoreReason.TOO_LARGE));
    }

    @Test
    void symlinksAreNotFollowed(@TempDir Path dir) throws Exception {
        Path outside = Files.createDirectories(dir.getParent().resolve(dir.getFileName() + "-outside"));
        Files.writeString(outside.resolve("Outside.java"), "class Outside {}");
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/A.java"), "class A {}");
        try {
            Files.createSymbolicLink(dir.resolve("link"), outside);
            Files.createSymbolicLink(dir.resolve("src/Linked.java"), outside.resolve("Outside.java"));
        } catch (UnsupportedOperationException | IOException e) {
            return; // platform without symlink support
        }
        Repository repo = new RepositoryScanner().scan(dir);
        assertEquals(List.of("src/A.java"), repo.javaFiles().stream().map(ScannedFile::relativePath).toList());
        assertTrue(repo.ignored().stream().anyMatch(i -> i.reason() == IgnoredFile.IgnoreReason.SYMLINK));
    }

    @Test
    void emptyRepositoryIsHandled(@TempDir Path dir) throws Exception {
        Repository repo = new RepositoryScanner().scan(dir);
        assertTrue(repo.isEmpty());
        assertEquals(0, repo.metadata().javaFileCount());
    }

    @Test
    void missingRootFails(@TempDir Path dir) {
        assertThrows(IOException.class, () -> new RepositoryScanner().scan(dir.resolve("nope")));
    }
}
