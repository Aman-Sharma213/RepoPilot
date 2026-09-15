package com.repopilot.git;

import com.repopilot.symbol.Symbol;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolIndex;
import com.repopilot.symbol.SymbolKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps changed line ranges onto symbols, deterministically.
 *
 * <p>For each changed range the most specific enclosing symbol wins: a change inside a method body
 * reports the method, not the whole class. Deleted files and files that are not indexed are
 * reported as such instead of being guessed at.
 */
public class ChangedSymbolLocator {

    /** @param unmappedFiles changed files that produced no symbol (deleted, non-Java, not indexed) */
    public record Result(List<SymbolId> symbols, Map<String, String> unmappedFiles) {

        public Result {
            symbols = List.copyOf(symbols);
            unmappedFiles = Map.copyOf(unmappedFiles);
        }
    }

    private final SymbolIndex index;

    public ChangedSymbolLocator(SymbolIndex index) {
        this.index = index;
    }

    public Result locate(List<FileDiff> diffs) {
        List<SymbolId> symbols = new ArrayList<>();
        Map<String, String> unmapped = new LinkedHashMap<>();
        for (FileDiff diff : diffs) {
            if (diff.binary()) {
                unmapped.put(diff.path(), "binary file");
                continue;
            }
            if (!diff.isJava()) {
                unmapped.put(diff.path(), "not a Java source file");
                continue;
            }
            if (diff.changeType() == DiffChangeType.DELETED) {
                unmapped.put(diff.path(), "file deleted; its symbols are no longer in the index");
                continue;
            }
            String path = diff.newPath();
            List<Symbol> inFile = index.symbolsInFile(path);
            if (inFile.isEmpty()) {
                unmapped.put(path, "no indexed symbols (file outside the index or not parsed)");
                continue;
            }
            List<SymbolId> found = new ArrayList<>();
            for (FileDiff.LineRange range : diff.changedLines()) {
                Symbol mostSpecific = null;
                for (Symbol symbol : inFile) {
                    if (symbol.kind() == SymbolKind.PARAMETER) {
                        continue;
                    }
                    if (!symbol.location().intersectsLines(range.start(), range.end())) {
                        continue;
                    }
                    if (mostSpecific == null || span(symbol) < span(mostSpecific)) {
                        mostSpecific = symbol;
                    }
                }
                if (mostSpecific != null && !found.contains(mostSpecific.id())) {
                    found.add(mostSpecific.id());
                }
            }
            if (found.isEmpty()) {
                unmapped.put(path, "changed lines are outside any declaration (imports, comments, whitespace)");
            }
            symbols.addAll(found);
        }
        return new Result(symbols.stream().distinct().sorted().toList(), unmapped);
    }

    private int span(Symbol symbol) {
        return symbol.location().endLine() - symbol.location().startLine();
    }
}
