package com.repopilot.ai;

import java.util.List;

/**
 * Outcome of resolving a natural-language change description to symbols.
 *
 * @param source     "groq" or "deterministic" - callers must be able to tell whether AI was used
 * @param ambiguous  true when no candidate reached the confidence threshold
 */
public record TargetResolution(List<RankedTarget> targets, List<RankedTarget> alternatives, boolean ambiguous,
                               String source, List<String> notes) {

    public TargetResolution {
        targets = List.copyOf(targets);
        alternatives = List.copyOf(alternatives);
        notes = List.copyOf(notes);
    }
}
