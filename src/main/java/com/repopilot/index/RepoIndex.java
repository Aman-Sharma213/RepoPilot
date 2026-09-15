package com.repopilot.index;

import com.repopilot.graph.CodeGraph;
import com.repopilot.incremental.FileRecord;
import com.repopilot.symbol.SymbolIndex;

import java.nio.file.Path;
import java.util.Map;

/** Everything the tool knows about a repository after indexing. */
public record RepoIndex(Path root, SymbolIndex symbols, CodeGraph graph, Map<String, FileRecord> files,
                        IndexStats stats) {

    public RepoIndex {
        files = Map.copyOf(files);
    }
}
