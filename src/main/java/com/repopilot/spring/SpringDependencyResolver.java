package com.repopilot.spring;

import com.repopilot.graph.EdgeType;
import com.repopilot.parse.AnnotationUse;
import com.repopilot.parse.FieldDeclaration;
import com.repopilot.parse.MethodDeclaration;
import com.repopilot.parse.ParameterDeclaration;
import com.repopilot.parse.ParseResult;
import com.repopilot.parse.TypeDeclaration;
import com.repopilot.parse.Types;
import com.repopilot.resolve.CompilationUnitContext;
import com.repopilot.resolve.Reference;
import com.repopilot.resolve.Resolution;
import com.repopilot.resolve.ResolutionLevel;
import com.repopilot.resolve.Scope;
import com.repopilot.resolve.SymbolResolver;
import com.repopilot.symbol.Symbol;
import com.repopilot.symbol.SymbolExtractor;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolIndex;
import com.repopilot.symbol.SymbolKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Framework-specific analysis for Spring, deliberately kept out of the generic Java resolver.
 *
 * <p>Spring wires beans at runtime, so a field of an interface type is really a dependency on the
 * implementation that Spring will inject. That relationship is invisible to plain Java resolution,
 * and it is modelled with its own edge types ({@link EdgeType#SPRING_DI},
 * {@link EdgeType#BEAN_CREATION}, {@link EdgeType#EVENT_LISTENER}) so it can be trusted, audited or
 * ignored independently of the deterministic Java graph.
 */
public class SpringDependencyResolver {

    public static final Set<String> STEREOTYPES = Set.of(
            "Service", "Component", "Repository", "Controller", "RestController", "Configuration",
            "ControllerAdvice", "RestControllerAdvice");
    public static final Set<String> INJECTION_ANNOTATIONS = Set.of("Autowired", "Inject", "Resource");

    private final SymbolIndex index;
    private final SymbolResolver resolver;

    public SpringDependencyResolver(SymbolIndex index, SymbolResolver resolver) {
        this.index = index;
        this.resolver = resolver;
    }

    public boolean isSpringBean(Symbol type) {
        return STEREOTYPES.stream().anyMatch(type::hasAnnotation);
    }

    public List<Reference> resolve(List<ParseResult> parseResults) {
        List<Reference> references = new ArrayList<>();
        for (ParseResult result : parseResults) {
            CompilationUnitContext unit = resolver.units()
                    .getOrDefault(result.file(), CompilationUnitContext.of(result));
            for (TypeDeclaration type : result.allTypes()) {
                String fqn = SymbolExtractor.qualify(result.packageName(), type.nestedName());
                Symbol typeSymbol = index.type(fqn).orElse(null);
                if (typeSymbol == null) {
                    continue;
                }
                Scope scope = Scope.of(result.file(), fqn);
                boolean bean = isSpringBean(typeSymbol);

                if (bean) {
                    for (FieldDeclaration field : type.fields()) {
                        boolean injected = field.annotations().stream()
                                .anyMatch(a -> INJECTION_ANNOTATIONS.contains(a.simpleName()));
                        if (injected) {
                            references.add(injection(unit, scope, SymbolId.ofField(fqn, field.name()),
                                    field.type(), qualifier(field.annotations()), field.location(),
                                    "@" + field.annotations().get(0).simpleName() + " " + field.type()
                                            + " " + field.name()));
                        }
                    }
                    List<MethodDeclaration> constructors = type.methods().stream()
                            .filter(MethodDeclaration::constructor).toList();
                    boolean singleConstructorInjection = constructors.size() == 1;
                    for (MethodDeclaration constructor : constructors) {
                        boolean annotated = constructor.annotations().stream()
                                .anyMatch(a -> INJECTION_ANNOTATIONS.contains(a.simpleName()));
                        if (!annotated && !singleConstructorInjection) {
                            continue;
                        }
                        SymbolId constructorId = SymbolId.ofConstructor(fqn, constructor.parameters().stream()
                                .map(ParameterDeclaration::erasedType).toList());
                        for (ParameterDeclaration parameter : constructor.parameters()) {
                            references.add(injection(unit, scope, constructorId, parameter.type(),
                                    null, parameter.location(),
                                    "constructor injection of " + parameter.type()));
                        }
                    }
                }

                for (MethodDeclaration method : type.methods()) {
                    List<String> paramTypes = method.parameters().stream()
                            .map(ParameterDeclaration::erasedType).toList();
                    SymbolId methodId = method.constructor()
                            ? SymbolId.ofConstructor(fqn, paramTypes)
                            : SymbolId.ofMethod(fqn, method.name(), paramTypes);
                    if (!index.contains(methodId)) {
                        continue;
                    }
                    if (method.annotations().stream().anyMatch(a -> a.simpleName().equals("Bean"))) {
                        references.add(new Reference(methodId, EdgeType.BEAN_CREATION,
                                resolver.typeResolver().resolveTypeName(unit, scope, method.returnType()),
                                method.location(), "@Bean " + method.returnType() + " " + method.name() + "()"));
                    }
                    if (method.annotations().stream().anyMatch(a -> a.simpleName().equals("EventListener"))
                            && !method.parameters().isEmpty()) {
                        ParameterDeclaration event = method.parameters().get(0);
                        references.add(new Reference(methodId, EdgeType.EVENT_LISTENER,
                                resolver.typeResolver().resolveTypeName(unit, scope, event.type()),
                                method.location(), "@EventListener on " + event.type()));
                    }
                }
            }
        }
        return references;
    }

    /**
     * Resolves an injection point. When the declared type is an interface, the injected object is
     * one of its implementations; a unique bean implementation is a confident answer, several
     * implementations are ambiguous unless {@code @Qualifier} or {@code @Primary} decides.
     */
    private Reference injection(CompilationUnitContext unit, Scope scope, SymbolId target, String declaredType,
                                String qualifier, com.repopilot.parse.SourceLocation location, String evidence) {
        Resolution declared = resolver.typeResolver().resolveTypeName(unit, scope, declaredType);
        if (!declared.isResolved()) {
            return new Reference(target, EdgeType.SPRING_DI,
                    Resolution.unresolved("injected type " + declaredType + " is outside the indexed sources"),
                    location, evidence);
        }
        String declaredFqn = declared.candidates().get(0).value();
        Symbol declaredSymbol = index.type(declaredFqn).orElse(null);
        if (declaredSymbol == null) {
            return new Reference(target, EdgeType.SPRING_DI, declared, location, evidence);
        }
        if (declaredSymbol.kind() != SymbolKind.INTERFACE) {
            return new Reference(target, EdgeType.SPRING_DI,
                    Resolution.resolved(declaredSymbol.id(), 0.95, ResolutionLevel.L4_FIELD_TYPE,
                            "Spring injects the concrete bean " + declaredFqn), location, evidence);
        }
        List<Symbol> implementations = index.types().stream()
                .filter(candidate -> resolver.inheritance().isSubtypeOf(candidate.id().value(), declaredFqn))
                .filter(candidate -> !candidate.id().value().equals(declaredFqn))
                .filter(this::isSpringBean)
                .toList();
        if (implementations.isEmpty()) {
            return new Reference(target, EdgeType.SPRING_DI,
                    Resolution.resolved(declaredSymbol.id(), 0.8, ResolutionLevel.L8_INTERFACE,
                            "no indexed bean implements " + declaredFqn + "; the interface itself is recorded"),
                    location, evidence);
        }
        if (qualifier != null) {
            List<Symbol> byQualifier = implementations.stream()
                    .filter(impl -> matchesQualifier(impl, qualifier)).toList();
            if (byQualifier.size() == 1) {
                return new Reference(target, EdgeType.SPRING_DI,
                        Resolution.resolved(byQualifier.get(0).id(), 0.95, ResolutionLevel.L8_INTERFACE,
                                "@Qualifier(\"" + qualifier + "\") selects " + byQualifier.get(0).id()),
                        location, evidence);
            }
        }
        List<Symbol> primary = implementations.stream().filter(impl -> impl.hasAnnotation("Primary")).toList();
        if (primary.size() == 1) {
            return new Reference(target, EdgeType.SPRING_DI,
                    Resolution.resolved(primary.get(0).id(), 0.9, ResolutionLevel.L8_INTERFACE,
                            "@Primary bean " + primary.get(0).id() + " implements " + declaredFqn),
                    location, evidence);
        }
        if (implementations.size() == 1) {
            return new Reference(target, EdgeType.SPRING_DI,
                    Resolution.resolved(implementations.get(0).id(), 0.9, ResolutionLevel.L8_INTERFACE,
                            "the only indexed bean implementing " + declaredFqn),
                    location, evidence);
        }
        return new Reference(target, EdgeType.SPRING_DI,
                Resolution.ambiguous(implementations.stream().map(Symbol::id).toList(),
                        ResolutionLevel.L8_INTERFACE,
                        implementations.size() + " beans implement " + declaredFqn
                                + " and no @Qualifier or @Primary decides which one is injected"),
                location, evidence);
    }

    private boolean matchesQualifier(Symbol bean, String qualifier) {
        String defaultName = Character.toLowerCase(bean.simpleName().charAt(0)) + bean.simpleName().substring(1);
        return qualifier.equals(defaultName) || qualifier.equalsIgnoreCase(bean.simpleName());
    }

    private String qualifier(List<AnnotationUse> annotations) {
        for (AnnotationUse annotation : annotations) {
            if (annotation.simpleName().equals("Qualifier")) {
                return annotation.firstStringArgument();
            }
            if (STEREOTYPES.contains(Types.simpleName(annotation.name()))) {
                String value = annotation.firstStringArgument();
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }
}
