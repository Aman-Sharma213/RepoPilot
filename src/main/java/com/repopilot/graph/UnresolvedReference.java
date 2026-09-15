package com.repopilot.graph;

import com.repopilot.parse.SourceLocation;
import com.repopilot.resolve.ResolutionStatus;
import com.repopilot.symbol.SymbolId;

/**
 * A relationship that exists in the source but could not be pointed at a symbol. Keeping these
 * is what allows the tool to report honest coverage instead of pretending everything resolved.
 */
public record UnresolvedReference(SymbolId source, EdgeType type, ResolutionStatus status, String rawText,
                                  String reason, SourceLocation location) {
}
