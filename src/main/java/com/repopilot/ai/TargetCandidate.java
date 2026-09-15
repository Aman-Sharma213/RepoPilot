package com.repopilot.ai;

import com.repopilot.symbol.SymbolId;

/** A symbol the deterministic retriever considers plausible for a natural-language description. */
public record TargetCandidate(SymbolId symbol, String kind, String file, int startLine, double score,
                              String signature) {
}
