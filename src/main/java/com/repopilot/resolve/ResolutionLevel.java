package com.repopilot.resolve;

/**
 * The resolution capability ladder from the build plan. Every resolution reports the level that
 * produced it, so the tool can state honestly how much semantic analysis was actually applied.
 */
public enum ResolutionLevel {
    NONE("no resolution"),
    L1_SAME_CLASS("same-class member"),
    L2_EXPLICIT_IMPORT("explicit import"),
    L3_SAME_PACKAGE("same package"),
    L4_FIELD_TYPE("field type"),
    L5_PARAMETER_TYPE("parameter or local variable type"),
    L6_RETURN_TYPE("chained call return type"),
    L7_INHERITANCE("superclass member"),
    L8_INTERFACE("interface member"),
    L9_OVERLOAD("overload selection"),
    L10_GENERICS("generic type variable");

    private final String description;

    ResolutionLevel(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
