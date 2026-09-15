package com.repopilot.analysis;

/** Contributor-facing classification for a relationship whose target was not indexed. */
public enum UnresolvedCategory {
    JDK_EXTERNAL,
    DEPENDENCY_EXTERNAL,
    PROJECT_LOCAL_UNRESOLVED,
    DYNAMIC
}
