package com.repopilot.parse;

import java.util.List;

/** An annotation applied to a declaration, with its raw argument text preserved as evidence. */
public record AnnotationUse(String name, List<String> arguments, SourceLocation location) {

    public AnnotationUse {
        arguments = List.copyOf(arguments);
    }

    public String simpleName() {
        return Types.simpleName(name);
    }

    /** First string literal argument, e.g. {@code @Qualifier("primary")} -&gt; {@code primary}. */
    public String firstStringArgument() {
        for (String a : arguments) {
            String t = a.trim();
            if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
                return t.substring(1, t.length() - 1);
            }
            int eq = t.indexOf('=');
            if (eq > 0) {
                String v = t.substring(eq + 1).trim();
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                    return v.substring(1, v.length() - 1);
                }
            }
        }
        return null;
    }
}
