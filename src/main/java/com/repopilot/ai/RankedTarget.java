package com.repopilot.ai;

import com.repopilot.symbol.SymbolId;

public record RankedTarget(SymbolId symbol, double confidence, String reason) {
}
