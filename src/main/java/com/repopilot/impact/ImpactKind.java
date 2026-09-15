package com.repopilot.impact;

public enum ImpactKind {
    /** The symbol itself changed, or is the resolved target of the request. */
    DIRECT,
    /** Reached through fully resolved edges. */
    INDIRECT,
    /** Reached only through ambiguous or low-confidence edges. */
    UNCERTAIN,
    /** Reached in test sources: useful to know which tests to run, reported separately. */
    TEST_IMPACT
}
