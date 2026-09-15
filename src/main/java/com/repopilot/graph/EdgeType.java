package com.repopilot.graph;

/** Relationship kinds in the code graph. */
public enum EdgeType {
    CALLS,
    EXTENDS,
    IMPLEMENTS,
    OVERRIDES,
    FIELD_TYPE,
    PARAMETER_TYPE,
    RETURN_TYPE,
    INSTANTIATES,
    IMPORTS,
    ANNOTATED_WITH,
    /** Spring dependency injection (constructor, setter or field injection). */
    SPRING_DI,
    /** Spring {@code @EventListener} / {@code ApplicationListener} relationship. */
    EVENT_LISTENER,
    /** {@code @Bean} factory method producing a type. */
    BEAN_CREATION,
    /** Reflective or otherwise non-statically-decidable relationship. */
    DYNAMIC;

    /** Edges that propagate impact when traversed in reverse (dependents of a changed symbol). */
    public boolean propagatesImpact() {
        return switch (this) {
            case CALLS, OVERRIDES, EXTENDS, IMPLEMENTS, FIELD_TYPE, PARAMETER_TYPE, RETURN_TYPE,
                 INSTANTIATES, SPRING_DI, EVENT_LISTENER, BEAN_CREATION, DYNAMIC -> true;
            case IMPORTS, ANNOTATED_WITH -> false;
        };
    }
}
