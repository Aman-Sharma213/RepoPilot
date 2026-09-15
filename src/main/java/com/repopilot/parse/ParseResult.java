package com.repopilot.parse;

import java.util.List;

/**
 * Syntax-level facts extracted from one compilation unit. This is <em>not</em> semantic analysis:
 * types are still raw text exactly as written in the source.
 */
public record ParseResult(String file, String packageName, List<ImportDeclaration> imports,
                          List<TypeDeclaration> types, boolean hasSyntaxErrors, List<String> diagnostics) {

    public ParseResult {
        imports = List.copyOf(imports);
        types = List.copyOf(types);
        diagnostics = List.copyOf(diagnostics);
    }

    public static ParseResult failed(String file, String message) {
        return new ParseResult(file, "", List.of(), List.of(), true, List.of(message));
    }

    /** Every declared type in the file, including nested types. */
    public List<TypeDeclaration> allTypes() {
        List<TypeDeclaration> all = new java.util.ArrayList<>();
        for (TypeDeclaration t : types) {
            all.addAll(t.selfAndNested());
        }
        return List.copyOf(all);
    }
}
