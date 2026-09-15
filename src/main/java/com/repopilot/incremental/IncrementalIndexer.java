package com.repopilot.incremental;

import com.repopilot.index.IndexingService;
import com.repopilot.index.ParseCoordinator;
import com.repopilot.index.RepoIndex;
import com.repopilot.parse.ParseResult;
import com.repopilot.scan.Repository;
import com.repopilot.scan.RepositoryScanner;
import com.repopilot.scan.ScannedFile;
import com.repopilot.util.Hashing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Re-indexes only what changed.
 *
 * <p>Parsing is per file and therefore incremental. Symbol resolution is not: a change in one file
 * can change how references in other files resolve, so the symbol index and the graph are always
 * rebuilt from the (mostly cached) syntax facts. This is an honest trade-off - it keeps the graph
 * correct while removing the expensive part of the work.
 */
public class IncrementalIndexer {

    /**
     * @param reparsedFiles files that were actually handed to Tree-sitter
     * @param reusedFiles   files served from the parse cache
     */
    public record Outcome(RepoIndex index, ChangeSet changes, List<String> reparsedFiles, List<String> reusedFiles) {
    }

    private final RepositoryScanner scanner;
    private final ParseCoordinator parseCoordinator;
    private final IndexingService indexingService;

    public IncrementalIndexer(RepositoryScanner scanner, ParseCoordinator parseCoordinator,
                              IndexingService indexingService) {
        this.scanner = scanner;
        this.parseCoordinator = parseCoordinator;
        this.indexingService = indexingService;
    }

    public Outcome update(Path root, Map<String, FileRecord> previousFiles, ParseCache cache) throws IOException {
        long started = System.nanoTime();
        Repository repository = scanner.scan(root);
        ChangeSet changes = new ChangeDetector().detect(repository, previousFiles);

        Set<String> mustParse = new HashSet<>(changes.requiresParsing());
        List<ScannedFile> toParse = new ArrayList<>();
        List<ParseResult> results = new ArrayList<>();
        List<String> reused = new ArrayList<>();

        for (ScannedFile file : repository.javaFiles()) {
            String hash = Hashing.sha256(file.absolutePath());
            ParseResult cached = mustParse.contains(file.relativePath())
                    ? null : cache.get(file.relativePath(), hash);
            if (cached != null) {
                results.add(cached);
                reused.add(file.relativePath());
            } else {
                toParse.add(file);
            }
        }
        List<ParseResult> fresh = parseCoordinator.parseAll(toParse);
        results.addAll(fresh);
        for (int i = 0; i < toParse.size(); i++) {
            ScannedFile file = toParse.get(i);
            cache.put(file.relativePath(), Hashing.sha256(file.absolutePath()), fresh.get(i));
        }
        for (String deleted : changes.paths(ChangeType.DELETED)) {
            cache.remove(deleted);
        }
        for (FileChange change : changes.changes()) {
            if (change.type() == ChangeType.RENAMED && change.previousPath() != null) {
                cache.remove(change.previousPath());
            }
        }

        results.sort(Comparator.comparing(ParseResult::file));
        RepoIndex index = indexingService.buildFrom(root, repository.javaFiles(), results, started,
                previousFiles.isEmpty() ? "full" : "incremental");
        List<String> reparsed = toParse.stream().map(ScannedFile::relativePath).sorted().toList();
        return new Outcome(index, changes, reparsed, reused.stream().sorted().toList());
    }
}
