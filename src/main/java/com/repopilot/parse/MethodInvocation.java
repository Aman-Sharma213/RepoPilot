package com.repopilot.parse;

import java.util.List;

/**
 * A syntactic method call site. Nothing here is resolved yet: {@code receiverText} is literally the
 * text before the dot, which Phase 5 turns into a type.
 */
public record MethodInvocation(String methodName, String receiverText, int argumentCount,
                               List<String> argumentTypeHints, boolean unqualified, boolean superCall,
                               boolean thisCall, SourceLocation location) {

    public MethodInvocation {
        // Unknown hints are null on purpose: the parser must not guess argument types.
        argumentTypeHints = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(argumentTypeHints));
    }
}
