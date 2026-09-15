package com.repopilot.analysis;

import com.repopilot.index.RepoIndex;
import com.repopilot.symbol.SymbolKind;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Metrics that explain repository scale without claiming that word count measures complexity. */
public record RepositoryStatistics(int indexedFiles, int productionFiles, int testFiles,
                                   long sourceLines, long sourceBytes, long typeSymbols,
                                   long callableSymbols, int syntaxErrorFiles) {

    public static RepositoryStatistics from(RepoIndex index) {
        int tests = (int) index.files().keySet().stream().filter(RepositoryStatistics::isTest).count();
        long bytes = index.files().values().stream().mapToLong(f -> f.size()).sum();
        long lines = 0;
        for (String relative : index.files().keySet()) {
            Path file = index.root().resolve(relative).normalize();
            if (!file.startsWith(index.root().toAbsolutePath().normalize()) || !Files.isRegularFile(file)) {
                continue;
            }
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                while (reader.readLine() != null) lines++;
            } catch (IOException ignored) {
                // The persisted file count remains authoritative if a file changed after indexing.
            }
        }
        long types = index.symbols().all().stream().filter(s -> s.kind().isType()).count();
        long callables = index.symbols().all().stream().filter(s -> s.kind().isCallable()).count();
        return new RepositoryStatistics(index.files().size(), index.files().size() - tests, tests, lines, bytes,
                types, callables, index.stats().filesWithSyntaxErrors());
    }

    private static boolean isTest(String file) {
        String normalized = file.replace('\\', '/');
        return normalized.contains("/src/test/") || normalized.startsWith("src/test/")
                || normalized.contains("/test/java/");
    }
}
