package com.repopilot.index;

import com.repopilot.graph.CodeGraph;
import com.repopilot.graph.GraphBuilder;
import com.repopilot.incremental.FileRecord;
import com.repopilot.parse.ParseResult;
import com.repopilot.parse.TreeSitterParser;
import com.repopilot.resolve.Reference;
import com.repopilot.resolve.SymbolResolver;
import com.repopilot.scan.Repository;
import com.repopilot.scan.RepositoryScanner;
import com.repopilot.scan.ScannedFile;
import com.repopilot.spring.SpringDependencyResolver;
import com.repopilot.symbol.SymbolExtractor;
import com.repopilot.symbol.SymbolIndex;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The deterministic pipeline: scan -&gt; parse -&gt; symbols -&gt; semantic resolution -&gt; graph.
 *
 * <p>No AI is involved here, by design: the graph must be identical whether or not an LLM is
 * reachable.
 */
public class IndexingService {

    private final RepositoryScanner scanner;
    private final SymbolExtractor symbolExtractor = new SymbolExtractor();
    private final GraphBuilder graphBuilder = new GraphBuilder();
    private final ParseCoordinator parseCoordinator;
    private final boolean springAnalysis;

    public IndexingService() {
        this(new RepositoryScanner(), ParseCoordinator.defaultCoordinator(), true);
    }

    public IndexingService(RepositoryScanner scanner, ParseCoordinator parseCoordinator, boolean springAnalysis) {
        this.scanner = scanner;
        this.parseCoordinator = parseCoordinator;
        this.springAnalysis = springAnalysis;
    }

    public RepoIndex index(Path root) throws IOException {
        long started = System.nanoTime();
        Repository repository = scanner.scan(root);
        List<ParseResult> parseResults = parseCoordinator.parseAll(repository.javaFiles());
        return buildFrom(root, repository.javaFiles(), parseResults, started, "full");
    }

    /** Builds symbols, resolution and graph from already parsed files. */
    public RepoIndex buildFrom(Path root, List<ScannedFile> files, List<ParseResult> parseResults, long startedNanos,
                               String mode) throws IOException {
        List<ParseResult> ordered = new ArrayList<>(parseResults);
        ordered.sort(Comparator.comparing(ParseResult::file));

        SymbolIndex symbols = new SymbolIndex();
        Map<String, Integer> symbolCounts = new LinkedHashMap<>();
        for (ParseResult result : ordered) {
            var extracted = symbolExtractor.extract(result);
            symbols.addAll(extracted);
            symbolCounts.put(result.file(), extracted.size());
        }

        SymbolResolver resolver = new SymbolResolver(symbols, ordered);
        List<Reference> references = new ArrayList<>(resolver.resolveAll(ordered));
        if (springAnalysis) {
            references.addAll(new SpringDependencyResolver(symbols, resolver).resolve(ordered));
        }
        CodeGraph graph = graphBuilder.build(symbols, references);

        Map<String, FileRecord> fileRecords = new LinkedHashMap<>();
        for (ScannedFile file : files) {
            fileRecords.put(file.relativePath(),
                    FileRecord.of(file, symbolCounts.getOrDefault(file.relativePath(), 0)));
        }

        int syntaxErrors = (int) ordered.stream().filter(ParseResult::hasSyntaxErrors).count();
        IndexStats stats = new IndexStats(files.size(), ordered.size(), syntaxErrors, symbols.size(),
                graph.nodeCount(), graph.edgeCount(), graph.unresolvedReferences().size(),
                (System.nanoTime() - startedNanos) / 1_000_000, graph.digest(), mode);
        return new RepoIndex(root, symbols, graph, fileRecords, stats);
    }

    public static TreeSitterParser newParser() {
        return new TreeSitterParser();
    }
}
