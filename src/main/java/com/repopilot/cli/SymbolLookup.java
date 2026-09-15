package com.repopilot.cli;

import com.repopilot.symbol.Symbol;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolIndex;

import java.util.List;

/** Resolves the {@code <symbol>} argument of {@code repo-pilot impact} to exactly one symbol. */
public class SymbolLookup {

    public record Match(List<SymbolId> candidates) {

        public boolean isUnique() {
            return candidates.size() == 1;
        }
    }

    private final SymbolIndex index;

    public SymbolLookup(SymbolIndex index) {
        this.index = index;
    }

    public Match find(String query) {
        String trimmed = query.trim();
        if (index.contains(new SymbolId(trimmed))) {
            return new Match(List.of(new SymbolId(trimmed)));
        }
        // Exact fully qualified type.
        if (index.type(trimmed).isPresent()) {
            return new Match(List.of(new SymbolId(trimmed)));
        }
        boolean wantsParameter = trimmed.contains("::");
        String normalised = trimmed;
        List<SymbolId> matches = index.all().stream()
                .filter(symbol -> wantsParameter
                        || symbol.kind() != com.repopilot.symbol.SymbolKind.PARAMETER)
                .filter(symbol -> matches(symbol, normalised))
                .map(Symbol::id)
                .sorted()
                .toList();
        return new Match(matches);
    }

    private boolean matches(Symbol symbol, String query) {
        String id = symbol.id().value();
        if (id.equals(query)) {
            return true;
        }
        String withoutArgs = id.contains("(") ? id.substring(0, id.indexOf('(')) : id;
        String queryWithoutArgs = query.contains("(") ? query.substring(0, query.indexOf('(')) : query;
        if (query.contains("(")) {
            // Signature given: require the same erased parameter list.
            return withoutArgs.endsWith(queryWithoutArgs.startsWith(".") ? queryWithoutArgs : "." + queryWithoutArgs)
                    && id.endsWith(query.substring(query.indexOf('(')))
                    || id.endsWith(query);
        }
        if (queryWithoutArgs.contains("#") || queryWithoutArgs.contains(".")) {
            return withoutArgs.endsWith(queryWithoutArgs) || withoutArgs.endsWith("." + queryWithoutArgs)
                    || withoutArgs.replace('#', '.').endsWith(queryWithoutArgs.replace('#', '.'));
        }
        return symbol.simpleName().equals(queryWithoutArgs);
    }
}
