package com.repopilot.resolve;

/**
 * Outcome of a resolution attempt. Making this explicit is a hard requirement of the project: a
 * relationship that could not be resolved is never silently dropped or invented.
 */
public enum ResolutionStatus {
    /** Exactly one target symbol, decided statically. */
    RESOLVED,
    /** Several plausible target symbols, no static way to choose. */
    AMBIGUOUS,
    /** No target could be determined (external library, missing source, unknown type). */
    UNRESOLVED,
    /** Statically undecidable by construction: reflection, method reference into unknown functional type, lambda dispatch. */
    DYNAMIC
}
