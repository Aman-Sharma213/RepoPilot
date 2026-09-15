package com.repopilot.resolve;

import com.repopilot.graph.EdgeType;
import com.repopilot.parse.AnnotationUse;
import com.repopilot.parse.FieldDeclaration;
import com.repopilot.parse.LocalVariable;
import com.repopilot.parse.MethodDeclaration;
import com.repopilot.parse.MethodInvocation;
import com.repopilot.parse.ObjectCreation;
import com.repopilot.parse.ParameterDeclaration;
import com.repopilot.parse.ParseResult;
import com.repopilot.parse.SourceLocation;
import com.repopilot.parse.TypeDeclaration;
import com.repopilot.parse.Types;
import com.repopilot.symbol.Symbol;
import com.repopilot.symbol.SymbolExtractor;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolIndex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks the parsed sources once more, this time with the full symbol index available, and produces
 * a resolved reference for every relationship: inheritance, member types, instantiation and calls.
 */
public class SymbolResolver {

    private final SymbolIndex index;
    private final ImportResolver importResolver;
    private final InheritanceResolver inheritance;
    private final MethodResolver methodResolver;
    private final TypeResolver typeResolver;
    private final Map<String, CompilationUnitContext> unitsByFile;

    public SymbolResolver(SymbolIndex index, List<ParseResult> parseResults) {
        this.index = index;
        Map<String, CompilationUnitContext> units = new LinkedHashMap<>();
        for (ParseResult result : parseResults) {
            units.put(result.file(), CompilationUnitContext.of(result));
        }
        this.unitsByFile = units;
        this.importResolver = new ImportResolver(index);
        this.inheritance = new InheritanceResolver(index, importResolver, units);
        this.methodResolver = new MethodResolver(index, inheritance);
        this.typeResolver = new TypeResolver(index, importResolver, inheritance, methodResolver, units);
    }

    public InheritanceResolver inheritance() {
        return inheritance;
    }

    public TypeResolver typeResolver() {
        return typeResolver;
    }

    public MethodResolver methodResolver() {
        return methodResolver;
    }

    public Map<String, CompilationUnitContext> units() {
        return unitsByFile;
    }

    /** Resolves every reference in the given compilation units, in deterministic order. */
    public List<Reference> resolveAll(List<ParseResult> parseResults) {
        List<Reference> references = new ArrayList<>();
        List<ParseResult> ordered = new ArrayList<>(parseResults);
        ordered.sort(Comparator.comparing(ParseResult::file));
        for (ParseResult result : ordered) {
            references.addAll(resolveFile(result));
        }
        return references;
    }

    public List<Reference> resolveFile(ParseResult result) {
        CompilationUnitContext unit = unitsByFile.computeIfAbsent(result.file(), f -> CompilationUnitContext.of(result));
        List<Reference> references = new ArrayList<>();
        for (TypeDeclaration type : result.allTypes()) {
            String fqn = SymbolExtractor.qualify(result.packageName(), type.nestedName());
            SymbolId typeId = SymbolId.ofType(fqn);
            if (!index.contains(typeId)) {
                continue;
            }
            Scope typeScope = typeScope(result, type, fqn);

            // Imports (file level, attributed to the type so that the graph stays symbol based).
            for (var imp : result.imports()) {
                Resolution resolution = importResolver.resolveType(unit, fqn, imp.qualifiedName());
                references.add(new Reference(typeId, EdgeType.IMPORTS, resolution, imp.location(),
                        "import " + imp.qualifiedName()));
            }

            // Inheritance.
            if (type.superclass() != null) {
                references.add(new Reference(typeId, EdgeType.EXTENDS,
                        typeResolver.resolveTypeName(unit, typeScope, type.superclass()), type.location(),
                        "extends " + type.superclass()));
            }
            for (String iface : type.interfaces()) {
                references.add(new Reference(typeId, EdgeType.IMPLEMENTS,
                        typeResolver.resolveTypeName(unit, typeScope, iface), type.location(),
                        (type.isInterface() ? "extends " : "implements ") + iface));
            }
            for (AnnotationUse annotation : type.annotations()) {
                references.add(new Reference(typeId, EdgeType.ANNOTATED_WITH,
                        typeResolver.resolveTypeName(unit, typeScope, annotation.name()), annotation.location(),
                        "@" + annotation.name()));
            }

            // Fields.
            for (FieldDeclaration field : type.fields()) {
                SymbolId fieldId = SymbolId.ofField(fqn, field.name());
                if (!index.contains(fieldId)) {
                    continue;
                }
                references.add(new Reference(fieldId, EdgeType.FIELD_TYPE,
                        typeResolver.resolveTypeName(unit, typeScope, field.type()), field.location(),
                        field.type() + " " + field.name()));
                if (field.initializerType() != null) {
                    references.add(new Reference(fieldId, EdgeType.INSTANTIATES,
                            typeResolver.resolveTypeName(unit, typeScope, field.initializerType()),
                            field.location(), "new " + field.initializerType() + "()"));
                }
                for (AnnotationUse annotation : field.annotations()) {
                    references.add(new Reference(fieldId, EdgeType.ANNOTATED_WITH,
                            typeResolver.resolveTypeName(unit, typeScope, annotation.name()),
                            annotation.location(), "@" + annotation.name()));
                }
            }

            // Methods.
            for (MethodDeclaration method : type.methods()) {
                List<String> paramTypes = method.parameters().stream()
                        .map(ParameterDeclaration::erasedType).toList();
                SymbolId methodId = method.constructor()
                        ? SymbolId.ofConstructor(fqn, paramTypes)
                        : SymbolId.ofMethod(fqn, method.name(), paramTypes);
                if (!index.contains(methodId)) {
                    continue;
                }
                Scope methodScope = methodScope(typeScope, method);

                if (!method.constructor() && !Types.isPrimitive(method.returnType())) {
                    references.add(new Reference(methodId, EdgeType.RETURN_TYPE,
                            typeResolver.resolveTypeName(unit, methodScope, method.returnType()),
                            method.location(), "returns " + method.returnType()));
                }
                for (ParameterDeclaration parameter : method.parameters()) {
                    if (Types.isPrimitive(parameter.type())) {
                        continue;
                    }
                    references.add(new Reference(methodId, EdgeType.PARAMETER_TYPE,
                            typeResolver.resolveTypeName(unit, methodScope, parameter.type()),
                            parameter.location(), "parameter " + parameter.type() + " " + parameter.name()));
                }
                for (AnnotationUse annotation : method.annotations()) {
                    references.add(new Reference(methodId, EdgeType.ANNOTATED_WITH,
                            typeResolver.resolveTypeName(unit, methodScope, annotation.name()),
                            annotation.location(), "@" + annotation.name()));
                }
                index.find(methodId).flatMap(inheritance::overriddenMethod).ifPresent(overridden ->
                        references.add(new Reference(methodId, EdgeType.OVERRIDES,
                                Resolution.resolved(overridden.id(), 1.0,
                                        index.type(overridden.ownerFqn())
                                                .map(s -> s.kind() == com.repopilot.symbol.SymbolKind.INTERFACE)
                                                .orElse(false)
                                                ? ResolutionLevel.L8_INTERFACE : ResolutionLevel.L7_INHERITANCE,
                                        "same name and parameter types as " + overridden.id()),
                                method.location(), method.name() + " overrides " + overridden.id())));

                for (ObjectCreation creation : method.objectCreations()) {
                    Resolution created = typeResolver.resolveTypeName(unit, methodScope, creation.type());
                    references.add(new Reference(methodId, EdgeType.INSTANTIATES, created, creation.location(),
                            "new " + creation.type() + "(" + creation.argumentCount() + " args)"));
                    if (created.isResolved()) {
                        Resolution constructor = methodResolver.resolveConstructor(
                                created.candidates().get(0).value(), creation.argumentCount(), List.of());
                        if (constructor.status() != ResolutionStatus.UNRESOLVED) {
                            references.add(new Reference(methodId, EdgeType.CALLS, constructor,
                                    creation.location(), "new " + creation.type() + "(...)"));
                        }
                    }
                }
                for (MethodInvocation invocation : method.invocations()) {
                    references.add(resolveInvocation(unit, methodScope, methodId, invocation));
                }
            }
        }
        references.sort(Comparator
                .comparing((Reference r) -> r.location().file())
                .thenComparingInt(r -> r.location().startLine())
                .thenComparing(r -> r.source().value())
                .thenComparing(r -> r.type().name())
                .thenComparing(Reference::rawText));
        return references;
    }

    private Reference resolveInvocation(CompilationUnitContext unit, Scope scope, SymbolId caller,
                                        MethodInvocation invocation) {
        SourceLocation location = invocation.location();
        String evidence = (invocation.receiverText().isEmpty() ? "" : invocation.receiverText() + ".")
                + invocation.methodName() + "(" + Math.max(invocation.argumentCount(), 0) + " args)";
        if (invocation.thisCall() && invocation.methodName().equals("this")) {
            Resolution constructor = methodResolver.resolveConstructor(scope.enclosingTypeFqn(),
                    invocation.argumentCount(), List.of());
            return new Reference(caller, EdgeType.CALLS, constructor, location, "this(...)");
        }
        if (invocation.superCall() && invocation.methodName().equals("super")) {
            List<SymbolId> supers = inheritance.directSupertypes(scope.enclosingTypeFqn());
            if (supers.isEmpty()) {
                return new Reference(caller, EdgeType.CALLS,
                        Resolution.unresolved("superclass constructor is outside the indexed sources"),
                        location, "super(...)");
            }
            return new Reference(caller, EdgeType.CALLS,
                    methodResolver.resolveConstructor(supers.get(0).value(), invocation.argumentCount(), List.of()),
                    location, "super(...)");
        }

        Resolution receiverType = typeResolver.resolveExpressionType(unit, scope, invocation.receiverText());
        if (!receiverType.isResolved()) {
            if (receiverType.status() == ResolutionStatus.DYNAMIC) {
                return new Reference(caller, EdgeType.DYNAMIC, receiverType, location, evidence);
            }
            return new Reference(caller, EdgeType.CALLS,
                    new Resolution(receiverType.status(), receiverType.candidates(), receiverType.confidence(),
                            receiverType.level(),
                            "receiver '" + invocation.receiverText() + "' unresolved: " + receiverType.reason()),
                    location, evidence);
        }
        String ownerFqn = receiverType.candidates().get(0).value();
        List<String> argumentTypes = resolveArgumentTypes(unit, scope, invocation);
        Resolution call = methodResolver.resolveMethod(ownerFqn, invocation.methodName(),
                invocation.argumentCount(), argumentTypes);
        double confidence = call.isResolved()
                ? Math.min(call.confidence(), receiverType.confidence()) : call.confidence();
        Resolution combined = new Resolution(call.status(), call.candidates(), confidence,
                call.level(), call.reason() + "; receiver: " + receiverType.reason());
        return new Reference(caller, EdgeType.CALLS, combined, location, evidence);
    }

    /** Best-effort static types of the arguments; unknown entries stay null. */
    private List<String> resolveArgumentTypes(CompilationUnitContext unit, Scope scope, MethodInvocation invocation) {
        List<String> resolved = new ArrayList<>();
        for (String hint : invocation.argumentTypeHints()) {
            if (hint == null) {
                resolved.add(null);
            } else if (hint.startsWith("identifier:")) {
                String name = hint.substring("identifier:".length());
                Resolution type = typeResolver.resolveExpressionType(unit, scope, name);
                resolved.add(type.isResolved() ? type.candidates().get(0).value() : null);
            } else if (Types.isPrimitive(hint) || hint.equals("String") || hint.equals("null")) {
                resolved.add(hint);
            } else {
                Resolution type = typeResolver.resolveTypeName(unit, scope, hint);
                resolved.add(type.isResolved() ? type.candidates().get(0).value() : hint);
            }
        }
        return resolved;
    }

    private Scope typeScope(ParseResult result, TypeDeclaration type, String fqn) {
        Map<String, Scope.Variable> variables = new LinkedHashMap<>();
        for (FieldDeclaration field : type.fields()) {
            variables.put(field.name(), new Scope.Variable(field.name(), field.type(), Scope.VariableKind.FIELD));
        }
        Map<String, String> typeVariables = new LinkedHashMap<>();
        type.typeParameters().forEach(tp -> putTypeVariable(typeVariables, tp));
        return new Scope(result.file(), fqn, variables, typeVariables, false);
    }

    private Scope methodScope(Scope typeScope, MethodDeclaration method) {
        Map<String, Scope.Variable> variables = new LinkedHashMap<>();
        for (ParameterDeclaration parameter : method.parameters()) {
            variables.put(parameter.name(),
                    new Scope.Variable(parameter.name(), parameter.type(), Scope.VariableKind.PARAMETER));
        }
        for (LocalVariable local : method.locals()) {
            String declared = "var".equals(local.declaredType()) && local.initializerType() != null
                    ? local.initializerType() : local.declaredType();
            variables.put(local.name(), new Scope.Variable(local.name(), declared, Scope.VariableKind.LOCAL));
        }
        Map<String, String> typeVariables = new LinkedHashMap<>(typeScope.typeVariables());
        method.typeParameters().forEach(tp -> putTypeVariable(typeVariables, tp));
        Scope merged = typeScope.with(variables);
        return new Scope(merged.file(), merged.enclosingTypeFqn(), merged.variables(), typeVariables,
                method.isStatic());
    }

    /** Splits a declared type parameter such as {@code T extends Base} into name and bound. */
    private static void putTypeVariable(Map<String, String> target, String declaration) {
        String text = declaration.trim();
        int extendsAt = text.indexOf(" extends ");
        if (extendsAt > 0) {
            target.put(text.substring(0, extendsAt).trim(), text.substring(extendsAt + 9).trim());
        } else {
            target.put(text, null);
        }
    }

    public SymbolIndex index() {
        return index;
    }
}
