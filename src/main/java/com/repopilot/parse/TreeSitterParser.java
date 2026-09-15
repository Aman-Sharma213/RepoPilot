package com.repopilot.parse;

import com.repopilot.security.ResourceLimits;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterJava;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses Java sources with Tree-sitter and extracts syntax facts.
 *
 * <p><strong>Scope.</strong> Tree-sitter produces a concrete syntax tree. It knows nothing about
 * types, imports resolution, inheritance or overload selection. Everything this class returns is
 * therefore raw source text with locations. Semantic meaning is added later, in Phase 5.
 *
 * <p><strong>Threading.</strong> A {@code TSParser} owns native state and is not thread safe, so
 * each thread gets its own instance through a {@link ThreadLocal}. Extraction itself is pure.
 */
public class TreeSitterParser implements AutoCloseable {

    private static final ThreadLocal<TSParser> PARSERS = ThreadLocal.withInitial(() -> {
        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterJava());
        return parser;
    });

    private final ResourceLimits limits;

    public TreeSitterParser() {
        this(ResourceLimits.defaults());
    }

    public TreeSitterParser(ResourceLimits limits) {
        this.limits = limits;
    }

    public ParseResult parseFile(Path file, String relativePath) {
        try {
            long size = Files.size(file);
            if (size > limits.maxFileBytes()) {
                return ParseResult.failed(relativePath, "file exceeds size limit (" + size + " bytes)");
            }
            byte[] bytes = Files.readAllBytes(file);
            return parse(new String(bytes, StandardCharsets.UTF_8), relativePath);
        } catch (IOException e) {
            return ParseResult.failed(relativePath, "unreadable: " + e.getClass().getSimpleName());
        }
    }

    /** Parses source text. Malformed input yields a result with {@code hasSyntaxErrors == true}. */
    public ParseResult parse(String source, String relativePath) {
        if (source == null) {
            return ParseResult.failed(relativePath, "null source");
        }
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        TSTree tree;
        try {
            tree = PARSERS.get().parseString(null, source);
        } catch (RuntimeException e) {
            return ParseResult.failed(relativePath, "parser failure: " + e.getClass().getSimpleName());
        }
        TSNode root = tree.getRootNode();
        Extractor extractor = new Extractor(bytes, relativePath);
        try {
            return extractor.extractCompilationUnit(root);
        } finally {
            // TSNode is only a native view into TSTree memory. Keep the owning tree strongly
            // reachable for the whole extraction; otherwise an optimizing JVM may run the
            // tree cleaner after getRootNode() and leave child traversal with a dangling pointer.
            java.lang.ref.Reference.reachabilityFence(tree);
        }
    }

    @Override
    public void close() {
        PARSERS.remove();
    }

    /** Stateless-per-file extraction from the concrete syntax tree. */
    private static final class Extractor {
        private final byte[] source;
        private final String file;
        private final List<String> diagnostics = new ArrayList<>();

        Extractor(byte[] source, String file) {
            this.source = source;
            this.file = file;
        }

        ParseResult extractCompilationUnit(TSNode root) {
            String packageName = "";
            List<ImportDeclaration> imports = new ArrayList<>();
            List<TypeDeclaration> types = new ArrayList<>();
            boolean errors = root.hasError();

            for (TSNode child : namedChildren(root)) {
                switch (child.getType()) {
                    case "package_declaration" -> packageName = extractPackage(child);
                    case "import_declaration" -> imports.add(extractImport(child));
                    case "class_declaration", "interface_declaration", "enum_declaration",
                         "record_declaration", "annotation_type_declaration" -> {
                        TypeDeclaration decl = extractType(child, "");
                        if (decl != null) {
                            types.add(decl);
                        }
                    }
                    case "ERROR" -> diagnostics.add("syntax error at line " + (child.getStartPoint().getRow() + 1));
                    default -> {
                        // comments, semicolons and other top-level trivia carry no symbols
                    }
                }
            }
            if (errors && diagnostics.isEmpty()) {
                diagnostics.add("tree-sitter reported a syntax error in " + file);
            }
            return new ParseResult(file, packageName, imports, types, errors, diagnostics);
        }

        private String extractPackage(TSNode node) {
            for (TSNode child : namedChildren(node)) {
                String type = child.getType();
                if (type.equals("scoped_identifier") || type.equals("identifier")) {
                    return text(child);
                }
            }
            return "";
        }

        private ImportDeclaration extractImport(TSNode node) {
            boolean isStatic = false;
            boolean onDemand = false;
            String name = "";
            int count = node.getChildCount();
            for (int i = 0; i < count; i++) {
                TSNode child = node.getChild(i);
                String type = child.getType();
                if (type.equals("static")) {
                    isStatic = true;
                } else if (type.equals("asterisk") || type.equals("*")) {
                    onDemand = true;
                } else if (type.equals("scoped_identifier") || type.equals("identifier")) {
                    name = text(child);
                }
            }
            return new ImportDeclaration(name, isStatic, onDemand, location(node));
        }

        private TypeDeclaration extractType(TSNode node, String outerPrefix) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode == null || nameNode.isNull()) {
                diagnostics.add("unnamed type declaration at line " + (node.getStartPoint().getRow() + 1));
                return null;
            }
            String simpleName = text(nameNode);
            String nestedName = outerPrefix.isEmpty() ? simpleName : outerPrefix + "." + simpleName;
            SyntaxKind kind = switch (node.getType()) {
                case "interface_declaration" -> SyntaxKind.INTERFACE;
                case "enum_declaration" -> SyntaxKind.ENUM;
                case "record_declaration" -> SyntaxKind.RECORD;
                case "annotation_type_declaration" -> SyntaxKind.ANNOTATION_TYPE;
                default -> SyntaxKind.CLASS;
            };

            Set<String> modifiers = new LinkedHashSet<>();
            List<AnnotationUse> annotations = new ArrayList<>();
            readModifiers(node, modifiers, annotations);

            String superclass = null;
            List<String> interfaces = new ArrayList<>();
            TSNode superclassNode = node.getChildByFieldName("superclass");
            if (superclassNode != null && !superclassNode.isNull()) {
                List<String> supers = typeList(superclassNode);
                if (!supers.isEmpty()) {
                    superclass = supers.get(0);
                }
            }
            TSNode interfacesNode = node.getChildByFieldName("interfaces");
            if (interfacesNode != null && !interfacesNode.isNull()) {
                interfaces.addAll(typeList(interfacesNode));
            }
            if (interfaces.isEmpty()) {
                // interface_declaration exposes "extends_interfaces" as a plain child, not a named field
                for (TSNode child : namedChildren(node)) {
                    if (child.getType().equals("extends_interfaces") || child.getType().equals("super_interfaces")) {
                        interfaces.addAll(typeList(child));
                    }
                }
            }
            interfaces = new ArrayList<>(new LinkedHashSet<>(interfaces));

            List<String> typeParameters = typeParameters(node.getChildByFieldName("type_parameters"));
            List<ParameterDeclaration> recordComponents = new ArrayList<>();
            TSNode params = node.getChildByFieldName("parameters");
            if (kind == SyntaxKind.RECORD && params != null && !params.isNull()) {
                recordComponents.addAll(parameters(params));
            }

            List<MethodDeclaration> methods = new ArrayList<>();
            List<FieldDeclaration> fields = new ArrayList<>();
            List<TypeDeclaration> nested = new ArrayList<>();
            List<String> enumConstants = new ArrayList<>();

            TSNode body = node.getChildByFieldName("body");
            if (body != null && !body.isNull()) {
                extractBody(body, nestedName, simpleName, methods, fields, nested, enumConstants);
            }
            // record components behave like final fields
            for (ParameterDeclaration component : recordComponents) {
                fields.add(new FieldDeclaration(component.name(), component.type(), Set.of("final"), List.of(),
                        null, component.location()));
            }

            return new TypeDeclaration(simpleName, nestedName, kind, superclass, interfaces, typeParameters,
                    modifiers, annotations, methods, fields, nested, enumConstants, recordComponents,
                    location(node));
        }

        private void extractBody(TSNode body, String nestedName, String simpleName,
                                 List<MethodDeclaration> methods, List<FieldDeclaration> fields,
                                 List<TypeDeclaration> nested, List<String> enumConstants) {
            for (TSNode member : namedChildren(body)) {
                switch (member.getType()) {
                    case "method_declaration" -> methods.add(extractMethod(member, false));
                    case "compact_constructor_declaration" -> methods.add(extractMethod(member, true));
                    case "constructor_declaration" -> methods.add(extractMethod(member, true));
                    case "field_declaration" -> fields.addAll(extractFields(member));
                    case "class_declaration", "interface_declaration", "enum_declaration",
                         "record_declaration", "annotation_type_declaration" -> {
                        TypeDeclaration inner = extractType(member, nestedName);
                        if (inner != null) {
                            nested.add(inner);
                        }
                    }
                    case "enum_constant" -> {
                        TSNode nameNode = member.getChildByFieldName("name");
                        if (nameNode != null && !nameNode.isNull()) {
                            enumConstants.add(text(nameNode));
                        }
                    }
                    case "enum_body_declarations" ->
                            extractBody(member, nestedName, simpleName, methods, fields, nested, enumConstants);
                    case "annotation_type_element_declaration" -> {
                        TSNode nameNode = member.getChildByFieldName("name");
                        TSNode typeNode = member.getChildByFieldName("type");
                        if (nameNode != null && !nameNode.isNull()) {
                            methods.add(new MethodDeclaration(text(nameNode),
                                    typeNode != null && !typeNode.isNull() ? text(typeNode) : "void",
                                    List.of(), Set.of("abstract"), List.of(), List.of(), List.of(), List.of(),
                                    List.of(), false, false, location(member)));
                        }
                    }
                    default -> {
                        // static/instance initialiser blocks and trivia declare no symbols
                    }
                }
            }
        }

        private MethodDeclaration extractMethod(TSNode node, boolean constructor) {
            TSNode nameNode = node.getChildByFieldName("name");
            String name = nameNode != null && !nameNode.isNull() ? text(nameNode) : "<unnamed>";
            TSNode returnTypeNode = node.getChildByFieldName("type");
            String returnType = constructor ? "void"
                    : (returnTypeNode != null && !returnTypeNode.isNull() ? text(returnTypeNode) : "void");
            Set<String> modifiers = new LinkedHashSet<>();
            List<AnnotationUse> annotations = new ArrayList<>();
            readModifiers(node, modifiers, annotations);
            List<ParameterDeclaration> params = parameters(node.getChildByFieldName("parameters"));
            List<String> typeParams = typeParameters(node.getChildByFieldName("type_parameters"));

            List<MethodInvocation> invocations = new ArrayList<>();
            List<ObjectCreation> creations = new ArrayList<>();
            List<LocalVariable> locals = new ArrayList<>();
            TSNode body = node.getChildByFieldName("body");
            boolean hasBody = body != null && !body.isNull();
            if (hasBody) {
                walkBody(body, invocations, creations, locals);
            } else if (node.getType().equals("compact_constructor_declaration")) {
                for (TSNode child : namedChildren(node)) {
                    if (child.getType().equals("block") || child.getType().equals("constructor_body")) {
                        hasBody = true;
                        walkBody(child, invocations, creations, locals);
                    }
                }
            }
            return new MethodDeclaration(name, returnType, params, modifiers, annotations, typeParams,
                    invocations, creations, locals, constructor, hasBody, location(node));
        }

        /** Depth-first walk of a method body collecting call sites, instantiations and locals. */
        private void walkBody(TSNode node, List<MethodInvocation> invocations, List<ObjectCreation> creations,
                              List<LocalVariable> locals) {
            String type = node.getType();
            switch (type) {
                case "method_invocation" -> invocations.add(extractInvocation(node));
                case "explicit_constructor_invocation" -> {
                    TSNode constructorNode = node.getChildByFieldName("constructor");
                    String name = constructorNode != null && !constructorNode.isNull() ? text(constructorNode) : "this";
                    int argc = argumentCount(node.getChildByFieldName("arguments"));
                    invocations.add(new MethodInvocation(name, name, argc,
                            argumentTypeHints(node.getChildByFieldName("arguments")),
                            false, name.equals("super"), name.equals("this"), location(node)));
                }
                case "method_reference" -> {
                    // Arity is unknown for a method reference: the functional interface decides it.
                    String raw = text(node);
                    int sep = raw.indexOf("::");
                    if (sep > 0) {
                        String receiver = raw.substring(0, sep).trim();
                        String method = raw.substring(sep + 2).trim();
                        invocations.add(new MethodInvocation(method, receiver, -1, List.of(), false,
                                receiver.equals("super"), receiver.equals("this"), location(node)));
                    }
                }
                case "object_creation_expression" -> {
                    TSNode typeNode = node.getChildByFieldName("type");
                    boolean anonymous = false;
                    for (TSNode child : namedChildren(node)) {
                        if (child.getType().equals("class_body")) {
                            anonymous = true;
                        }
                    }
                    creations.add(new ObjectCreation(typeNode != null && !typeNode.isNull() ? text(typeNode) : "",
                            argumentCount(node.getChildByFieldName("arguments")), anonymous, location(node)));
                }
                case "local_variable_declaration" -> {
                    TSNode typeNode = node.getChildByFieldName("type");
                    String declaredType = typeNode != null && !typeNode.isNull() ? text(typeNode) : "var";
                    for (TSNode child : namedChildren(node)) {
                        if (child.getType().equals("variable_declarator")) {
                            TSNode nameNode = child.getChildByFieldName("name");
                            TSNode valueNode = child.getChildByFieldName("value");
                            String initType = null;
                            if (valueNode != null && !valueNode.isNull()
                                    && valueNode.getType().equals("object_creation_expression")) {
                                TSNode created = valueNode.getChildByFieldName("type");
                                if (created != null && !created.isNull()) {
                                    initType = stripEmptyDiamond(text(created));
                                }
                            }
                            if (nameNode != null && !nameNode.isNull()) {
                                locals.add(new LocalVariable(text(nameNode), declaredType, initType,
                                        location(child)));
                            }
                        }
                    }
                }
                default -> {
                    // keep walking
                }
            }
            for (TSNode child : namedChildren(node)) {
                walkBody(child, invocations, creations, locals);
            }
        }

        private MethodInvocation extractInvocation(TSNode node) {
            TSNode nameNode = node.getChildByFieldName("name");
            TSNode objectNode = node.getChildByFieldName("object");
            String methodName = nameNode != null && !nameNode.isNull() ? text(nameNode) : "<unnamed>";
            boolean unqualified = objectNode == null || objectNode.isNull();
            String receiver = unqualified ? "" : text(objectNode);
            boolean superCall = !unqualified && objectNode.getType().equals("super");
            boolean thisCall = !unqualified && receiver.equals("this");
            TSNode arguments = node.getChildByFieldName("arguments");
            return new MethodInvocation(methodName, receiver, argumentCount(arguments),
                    argumentTypeHints(arguments), unqualified, superCall, thisCall, location(node));
        }

        /**
         * Syntactic hints for argument types: literals and {@code new X()} expressions are obvious,
         * anything else is reported as {@code null} so that Phase 5 does not over-claim knowledge.
         */
        private List<String> argumentTypeHints(TSNode arguments) {
            if (arguments == null || arguments.isNull()) {
                return List.of();
            }
            List<String> hints = new ArrayList<>();
            for (TSNode arg : namedChildren(arguments)) {
                hints.add(switch (arg.getType()) {
                    case "string_literal" -> "String";
                    case "decimal_integer_literal", "hex_integer_literal", "octal_integer_literal" -> "int";
                    case "decimal_floating_point_literal" -> "double";
                    case "true", "false" -> "boolean";
                    case "character_literal" -> "char";
                    case "null_literal" -> "null";
                    case "object_creation_expression" -> {
                        TSNode t = arg.getChildByFieldName("type");
                        yield t != null && !t.isNull() ? text(t) : null;
                    }
                    case "identifier" -> "identifier:" + text(arg);
                    default -> null;
                });
            }
            return hints;
        }

        private int argumentCount(TSNode arguments) {
            if (arguments == null || arguments.isNull()) {
                return 0;
            }
            return arguments.getNamedChildCount();
        }

        private List<FieldDeclaration> extractFields(TSNode node) {
            Set<String> modifiers = new LinkedHashSet<>();
            List<AnnotationUse> annotations = new ArrayList<>();
            readModifiers(node, modifiers, annotations);
            TSNode typeNode = node.getChildByFieldName("type");
            String type = typeNode != null && !typeNode.isNull() ? text(typeNode) : "";
            List<FieldDeclaration> fields = new ArrayList<>();
            for (TSNode child : namedChildren(node)) {
                if (!child.getType().equals("variable_declarator")) {
                    continue;
                }
                TSNode nameNode = child.getChildByFieldName("name");
                TSNode valueNode = child.getChildByFieldName("value");
                String initType = null;
                if (valueNode != null && !valueNode.isNull()
                        && valueNode.getType().equals("object_creation_expression")) {
                    TSNode created = valueNode.getChildByFieldName("type");
                    if (created != null && !created.isNull()) {
                        initType = stripEmptyDiamond(text(created));
                    }
                }
                if (nameNode != null && !nameNode.isNull()) {
                    fields.add(new FieldDeclaration(text(nameNode), type, modifiers, annotations, initType,
                            location(child)));
                }
            }
            return fields;
        }

        private List<ParameterDeclaration> parameters(TSNode params) {
            if (params == null || params.isNull()) {
                return List.of();
            }
            List<ParameterDeclaration> result = new ArrayList<>();
            for (TSNode param : namedChildren(params)) {
                switch (param.getType()) {
                    case "formal_parameter", "receiver_parameter" -> {
                        TSNode typeNode = param.getChildByFieldName("type");
                        TSNode nameNode = param.getChildByFieldName("name");
                        if (typeNode != null && !typeNode.isNull() && nameNode != null && !nameNode.isNull()) {
                            result.add(new ParameterDeclaration(text(nameNode), text(typeNode), false,
                                    location(param)));
                        }
                    }
                    case "spread_parameter" -> {
                        TSNode nameNode = null;
                        String type = null;
                        for (TSNode child : namedChildren(param)) {
                            if (child.getType().equals("variable_declarator")) {
                                nameNode = child.getChildByFieldName("name");
                            } else if (type == null) {
                                type = text(child);
                            }
                        }
                        if (nameNode != null && !nameNode.isNull()) {
                            result.add(new ParameterDeclaration(text(nameNode), (type == null ? "Object" : type),
                                    true, location(param)));
                        }
                    }
                    default -> {
                        // annotations on the parameter list itself
                    }
                }
            }
            return result;
        }

        private List<String> typeParameters(TSNode node) {
            if (node == null || node.isNull()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (TSNode child : namedChildren(node)) {
                if (child.getType().equals("type_parameter")) {
                    // Keep the bound: "T" or "T extends Base". Phase 5 needs the bound for level 10.
                    result.add(text(child).trim());
                }
            }
            return result;
        }

        private List<String> typeList(TSNode node) {
            List<String> result = new ArrayList<>();
            for (TSNode child : namedChildren(node)) {
                switch (child.getType()) {
                    case "type_list" -> result.addAll(typeList(child));
                    case "type_identifier", "generic_type", "scoped_type_identifier" -> result.add(text(child));
                    default -> {
                        // modifiers/keywords
                    }
                }
            }
            return result;
        }

        private void readModifiers(TSNode node, Set<String> modifiers, List<AnnotationUse> annotations) {
            for (TSNode child : namedChildren(node)) {
                if (!child.getType().equals("modifiers")) {
                    continue;
                }
                int count = child.getChildCount();
                for (int i = 0; i < count; i++) {
                    TSNode mod = child.getChild(i);
                    String type = mod.getType();
                    if (type.equals("marker_annotation") || type.equals("annotation")) {
                        annotations.add(annotation(mod));
                    } else {
                        modifiers.add(text(mod));
                    }
                }
            }
        }

        private AnnotationUse annotation(TSNode node) {
            TSNode nameNode = node.getChildByFieldName("name");
            String name = nameNode != null && !nameNode.isNull() ? text(nameNode) : "";
            List<String> args = new ArrayList<>();
            TSNode argsNode = node.getChildByFieldName("arguments");
            if (argsNode != null && !argsNode.isNull()) {
                for (TSNode arg : namedChildren(argsNode)) {
                    args.add(text(arg));
                }
            }
            return new AnnotationUse(name, args, location(node));
        }

        /** {@code new HashMap<>()} declares type {@code HashMap}; the diamond carries no information. */
        private static String stripEmptyDiamond(String type) {
            return type.endsWith("<>") ? type.substring(0, type.length() - 2).trim() : type;
        }

        private List<TSNode> namedChildren(TSNode node) {
            int count = node.getNamedChildCount();
            List<TSNode> children = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                TSNode child = node.getNamedChild(i);
                if (child != null && !child.isNull()) {
                    children.add(child);
                }
            }
            return children;
        }

        private String text(TSNode node) {
            int start = Math.max(0, node.getStartByte());
            int end = Math.min(source.length, node.getEndByte());
            if (end <= start) {
                return "";
            }
            return new String(source, start, end - start, StandardCharsets.UTF_8);
        }

        private SourceLocation location(TSNode node) {
            return new SourceLocation(file,
                    node.getStartPoint().getRow() + 1,
                    node.getEndPoint().getRow() + 1,
                    node.getStartPoint().getColumn() + 1,
                    node.getEndPoint().getColumn() + 1,
                    node.getStartByte(), node.getEndByte());
        }
    }

    /** Flattens a parse result into uniform syntax nodes (used by tests and {@code --debug} output). */
    public static List<SyntaxNode> flatten(ParseResult result) {
        List<SyntaxNode> nodes = new ArrayList<>();
        if (!result.packageName().isEmpty()) {
            nodes.add(SyntaxNode.of(SyntaxKind.PACKAGE, result.packageName(), SourceLocation.unknown(result.file())));
        }
        for (ImportDeclaration imp : result.imports()) {
            nodes.add(SyntaxNode.of(SyntaxKind.IMPORT, imp.qualifiedName(), imp.location()));
        }
        for (TypeDeclaration type : result.allTypes()) {
            nodes.add(new SyntaxNode(type.kind(), type.nestedName(), type.location(),
                    Map.of("superclass", String.valueOf(type.superclass()),
                            "interfaces", String.join(",", type.interfaces())), List.of()));
            if (type.superclass() != null) {
                nodes.add(SyntaxNode.of(SyntaxKind.EXTENDS, type.superclass(), type.location()));
            }
            for (String iface : type.interfaces()) {
                nodes.add(SyntaxNode.of(SyntaxKind.IMPLEMENTS, iface, type.location()));
            }
            for (FieldDeclaration field : type.fields()) {
                nodes.add(new SyntaxNode(SyntaxKind.FIELD, field.name(), field.location(),
                        Map.of("type", field.type()), List.of()));
            }
            for (MethodDeclaration method : type.methods()) {
                List<SyntaxNode> children = new ArrayList<>();
                for (ParameterDeclaration p : method.parameters()) {
                    children.add(new SyntaxNode(SyntaxKind.PARAMETER, p.name(), p.location(),
                            Map.of("type", p.type()), List.of()));
                }
                for (MethodInvocation inv : method.invocations()) {
                    children.add(new SyntaxNode(SyntaxKind.METHOD_INVOCATION, inv.methodName(), inv.location(),
                            Map.of("receiver", inv.receiverText(), "args", String.valueOf(inv.argumentCount())),
                            List.of()));
                }
                for (ObjectCreation creation : method.objectCreations()) {
                    children.add(new SyntaxNode(SyntaxKind.OBJECT_CREATION, creation.type(), creation.location(),
                            Map.of("args", String.valueOf(creation.argumentCount())), List.of()));
                }
                for (LocalVariable local : method.locals()) {
                    children.add(new SyntaxNode(SyntaxKind.LOCAL_VARIABLE, local.name(), local.location(),
                            Map.of("type", local.declaredType()), List.of()));
                }
                nodes.add(new SyntaxNode(method.constructor() ? SyntaxKind.CONSTRUCTOR : SyntaxKind.METHOD,
                        method.name(), method.location(), Map.of("returns", method.returnType()), children));
            }
            for (AnnotationUse annotation : type.annotations()) {
                nodes.add(SyntaxNode.of(SyntaxKind.ANNOTATION_USE, annotation.name(), annotation.location()));
            }
        }
        nodes.sort(Comparator.comparingInt((SyntaxNode n) -> n.location().startLine())
                .thenComparing(SyntaxNode::name, Comparator.nullsFirst(Comparator.naturalOrder())));
        return nodes;
    }
}
