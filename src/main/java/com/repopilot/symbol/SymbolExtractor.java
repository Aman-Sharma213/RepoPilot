package com.repopilot.symbol;

import com.repopilot.parse.AnnotationUse;
import com.repopilot.parse.FieldDeclaration;
import com.repopilot.parse.MethodDeclaration;
import com.repopilot.parse.ParameterDeclaration;
import com.repopilot.parse.ParseResult;
import com.repopilot.parse.TypeDeclaration;
import com.repopilot.parse.Types;

import java.util.ArrayList;
import java.util.List;

/** Turns syntax facts (Phase 3) into stably identified symbols (Phase 4). */
public class SymbolExtractor {

    public List<Symbol> extract(ParseResult parseResult) {
        List<Symbol> symbols = new ArrayList<>();
        for (TypeDeclaration type : parseResult.allTypes()) {
            String fqn = qualify(parseResult.packageName(), type.nestedName());
            List<String> superTypes = new ArrayList<>();
            if (type.superclass() != null) {
                superTypes.add(type.superclass());
            }
            superTypes.addAll(type.interfaces());
            symbols.add(new Symbol(SymbolId.ofType(fqn), SymbolKind.fromSyntax(type.kind()), type.simpleName(),
                    parseResult.packageName(), enclosing(parseResult.packageName(), type.nestedName()),
                    List.of(), null, superTypes, type.modifiers(), annotationNames(type.annotations()),
                    type.typeParameters(), type.location()));

            for (MethodDeclaration method : type.methods()) {
                List<String> paramTypes = method.parameters().stream()
                        .map(ParameterDeclaration::erasedType).toList();
                SymbolId methodId = method.constructor()
                        ? SymbolId.ofConstructor(fqn, paramTypes)
                        : SymbolId.ofMethod(fqn, method.name(), paramTypes);
                symbols.add(new Symbol(methodId,
                        method.constructor() ? SymbolKind.CONSTRUCTOR : SymbolKind.METHOD,
                        method.constructor() ? SymbolId.CONSTRUCTOR_NAME : method.name(),
                        parseResult.packageName(), fqn, paramTypes, method.returnType(), List.of(),
                        method.modifiers(), annotationNames(method.annotations()), method.typeParameters(),
                        method.location()));
                for (ParameterDeclaration parameter : method.parameters()) {
                    symbols.add(new Symbol(SymbolId.ofParameter(methodId, parameter.name()), SymbolKind.PARAMETER,
                            parameter.name(), parseResult.packageName(), methodId.value(), List.of(),
                            parameter.type(), List.of(), java.util.Set.of(), List.of(), List.of(),
                            parameter.location()));
                }
            }
            for (FieldDeclaration field : type.fields()) {
                symbols.add(new Symbol(SymbolId.ofField(fqn, field.name()), SymbolKind.FIELD, field.name(),
                        parseResult.packageName(), fqn, List.of(), field.type(), List.of(), field.modifiers(),
                        annotationNames(field.annotations()), List.of(), field.location()));
            }
        }
        return symbols;
    }

    /** Fully qualified name for a possibly nested type. */
    public static String qualify(String packageName, String nestedName) {
        return packageName == null || packageName.isEmpty() ? nestedName : packageName + "." + nestedName;
    }

    private static String enclosing(String packageName, String nestedName) {
        int dot = nestedName.lastIndexOf('.');
        return dot < 0 ? packageName : qualify(packageName, nestedName.substring(0, dot));
    }

    private static List<String> annotationNames(List<AnnotationUse> annotations) {
        return annotations.stream().map(a -> Types.simpleName(a.name())).toList();
    }
}
