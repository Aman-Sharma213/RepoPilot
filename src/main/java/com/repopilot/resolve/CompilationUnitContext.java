package com.repopilot.resolve;

import com.repopilot.parse.ImportDeclaration;
import com.repopilot.parse.ParseResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-file information needed to turn a written type name into a fully qualified name. */
public record CompilationUnitContext(String file, String packageName, Map<String, String> singleTypeImports,
                                     List<String> onDemandPackages, List<String> staticImports,
                                     List<String> declaredTypes) {

    public CompilationUnitContext {
        singleTypeImports = Map.copyOf(singleTypeImports);
        onDemandPackages = List.copyOf(onDemandPackages);
        staticImports = List.copyOf(staticImports);
        declaredTypes = List.copyOf(declaredTypes);
    }

    public static CompilationUnitContext of(ParseResult result) {
        Map<String, String> single = new LinkedHashMap<>();
        List<String> onDemand = new ArrayList<>();
        List<String> statics = new ArrayList<>();
        for (ImportDeclaration imp : result.imports()) {
            if (imp.onDemand()) {
                onDemand.add(imp.qualifiedName());
            } else if (imp.isStatic()) {
                statics.add(imp.qualifiedName());
            } else {
                single.put(imp.simpleName(), imp.qualifiedName());
            }
        }
        List<String> declared = result.allTypes().stream()
                .map(t -> com.repopilot.symbol.SymbolExtractor.qualify(result.packageName(), t.nestedName()))
                .toList();
        return new CompilationUnitContext(result.file(), result.packageName(), single, onDemand, statics, declared);
    }
}
