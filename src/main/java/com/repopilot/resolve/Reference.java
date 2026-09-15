package com.repopilot.resolve;

import com.repopilot.graph.EdgeType;
import com.repopilot.parse.SourceLocation;
import com.repopilot.symbol.SymbolId;

/**
 * One relationship discovered in the source, together with how it was resolved.
 *
 * @param rawText the source text that produced the reference - the evidence shown to users
 */
public record Reference(SymbolId source, EdgeType type, Resolution resolution, SourceLocation location,
                        String rawText) {
}
