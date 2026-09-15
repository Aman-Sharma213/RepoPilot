package com.repopilot.graph;

import com.repopilot.symbol.Symbol;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolKind;

/** A node is always a symbol that really exists in the indexed sources. */
public record GraphNode(SymbolId id, SymbolKind kind, String file, int startLine, int endLine, boolean testCode) {

    public static GraphNode of(Symbol symbol) {
        return new GraphNode(symbol.id(), symbol.kind(), symbol.file(), symbol.location().startLine(),
                symbol.location().endLine(), symbol.isTestSymbol());
    }
}
