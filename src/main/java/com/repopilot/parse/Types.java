package com.repopilot.parse;

/** Purely syntactic helpers for type strings. No semantic knowledge lives here. */
public final class Types {

    private Types() {
    }

    /** {@code Map<String, List<Integer>>} -&gt; {@code Map}; {@code int[]} -&gt; {@code int}. */
    public static String erase(String type) {
        if (type == null || type.isBlank()) {
            return type;
        }
        String t = type.trim();
        int angle = t.indexOf('<');
        if (angle >= 0) {
            t = t.substring(0, angle);
        }
        t = t.replace("...", "").replace("[]", "").trim();
        if (t.startsWith("@")) {
            // annotated type use such as "@NonNull String"
            int space = t.lastIndexOf(' ');
            if (space > 0) {
                t = t.substring(space + 1);
            }
        }
        return t;
    }

    /**
     * Erasure used for symbol identity: generic arguments are dropped but array dimensions are
     * kept, because {@code go(int)} and {@code go(int[])} are different methods in Java. A varargs
     * parameter is an array parameter.
     */
    public static String parameterErasure(String type, boolean varargs) {
        if (type == null || type.isBlank()) {
            return type;
        }
        String t = type.trim();
        int dimensions = 0;
        while (t.endsWith("[]")) {
            dimensions++;
            t = t.substring(0, t.length() - 2).trim();
        }
        if (t.endsWith("...")) {
            dimensions++;
            t = t.substring(0, t.length() - 3).trim();
        }
        if (varargs) {
            dimensions++;
        }
        return erase(t) + "[]".repeat(dimensions);
    }

    public static boolean isArray(String type) {
        return type != null && (type.contains("[]") || type.contains("..."));
    }

    public static boolean isGeneric(String type) {
        return type != null && type.indexOf('<') >= 0;
    }

    /** Last segment of a dotted name: {@code java.util.List} -&gt; {@code List}. */
    public static String simpleName(String qualified) {
        if (qualified == null) {
            return null;
        }
        String erased = erase(qualified);
        int dot = erased.lastIndexOf('.');
        return dot >= 0 ? erased.substring(dot + 1) : erased;
    }

    public static boolean isPrimitive(String type) {
        return switch (erase(type)) {
            case "int", "long", "short", "byte", "char", "boolean", "float", "double", "void" -> true;
            default -> false;
        };
    }
}
