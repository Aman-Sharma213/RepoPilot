package com.repopilot.index;

import com.repopilot.graph.CodeGraph;
import com.repopilot.parse.ParseResult;
import com.repopilot.parse.TreeSitterParser;
import com.repopilot.resolve.Reference;
import com.repopilot.resolve.SymbolResolver;
import com.repopilot.symbol.SymbolExtractor;
import com.repopilot.symbol.SymbolIndex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory repository fixture used by the resolution and graph tests. */
public final class TestRepo {

    private final Map<String, String> sources = new LinkedHashMap<>();

    public static TestRepo of() {
        return new TestRepo();
    }

    public TestRepo file(String path, String source) {
        sources.put(path, source);
        return this;
    }

    public List<ParseResult> parse() {
        TreeSitterParser parser = new TreeSitterParser();
        List<ParseResult> results = new ArrayList<>();
        sources.forEach((path, source) -> results.add(parser.parse(source, path)));
        return results;
    }

    public SymbolIndex symbols() {
        SymbolIndex index = new SymbolIndex();
        SymbolExtractor extractor = new SymbolExtractor();
        parse().forEach(r -> index.addAll(extractor.extract(r)));
        return index;
    }

    public List<Reference> references() {
        List<ParseResult> parsed = parse();
        return new SymbolResolver(symbols(), parsed).resolveAll(parsed);
    }

    public SymbolResolver resolver() {
        return new SymbolResolver(symbols(), parse());
    }

    public RepoIndex index() throws IOException {
        Path dir = Files.createTempDirectory("repo-pilot-test");
        return writeTo(dir);
    }

    /** Materialises the fixture on disk and runs the real pipeline over it. */
    public RepoIndex writeTo(Path dir) throws IOException {
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            Path file = dir.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue());
        }
        return new IndexingService().index(dir);
    }

    public CodeGraph graph() throws IOException {
        return index().graph();
    }
}
