package com.repopilot.parse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TreeSitterParserTest {

    private final TreeSitterParser parser = new TreeSitterParser();

    @Test
    void extractsPackageImportsAndTypes() {
        ParseResult r = parser.parse("""
                package com.example.auth;

                import java.util.List;
                import static java.util.Objects.requireNonNull;
                import java.io.*;

                class A {}
                interface B {}
                class C extends A implements B {}
                """, "src/T.java");

        assertFalse(r.hasSyntaxErrors());
        assertEquals("com.example.auth", r.packageName());
        assertEquals(3, r.imports().size());
        assertTrue(r.imports().get(1).isStatic());
        assertTrue(r.imports().get(2).onDemand());
        assertEquals(List.of("A", "B", "C"), r.types().stream().map(TypeDeclaration::simpleName).toList());
        TypeDeclaration c = r.types().get(2);
        assertEquals("A", c.superclass());
        assertEquals(List.of("B"), c.interfaces());
        assertEquals(SyntaxKind.INTERFACE, r.types().get(1).kind());
    }

    @Test
    void extractsInterfaceExtendsList() {
        ParseResult r = parser.parse("interface X extends Y, Z {}", "src/X.java");
        assertEquals(List.of("Y", "Z"), r.types().get(0).interfaces());
    }

    @Test
    void extractsMethodsWithParametersAndReturnTypes() {
        ParseResult r = parser.parse("""
                class S {
                  private final java.util.Map<String, Integer> counts = new java.util.HashMap<>();
                  S(String name) {}
                  public int foo() { return 0; }
                  public int foo(int a) { return a; }
                  public int foo(int a, String b) { return a; }
                  static <T> T pick(T... values) { return values[0]; }
                  abstract void gone();
                }
                """, "src/S.java");
        TypeDeclaration s = r.types().get(0);
        assertEquals(1, s.fields().size());
        FieldDeclaration field = s.fields().get(0);
        assertEquals("counts", field.name());
        assertEquals("java.util.Map<String, Integer>", field.type());
        assertEquals("java.util.Map", field.erasedType());
        assertEquals("java.util.HashMap", field.initializerType());

        List<MethodDeclaration> foos = s.methods().stream().filter(m -> m.name().equals("foo")).toList();
        assertEquals(3, foos.size());
        assertEquals(List.of(0, 1, 2), foos.stream().map(MethodDeclaration::arity).toList());
        assertTrue(s.methods().get(0).constructor());
        MethodDeclaration pick = s.methods().stream().filter(m -> m.name().equals("pick")).findFirst().orElseThrow();
        assertTrue(pick.isStatic());
        assertTrue(pick.isVarargs());
        assertEquals(List.of("T"), pick.typeParameters());
        assertTrue(s.methods().stream().filter(m -> m.name().equals("gone")).findFirst().orElseThrow().isAbstract());
    }

    @Test
    void extractsInvocationsWithArityAndReceiver() {
        ParseResult r = parser.parse("""
                class I {
                  void run(Helper helper) {
                    foo();
                    foo(1);
                    foo(1, "x");
                    helper.help();
                    this.foo();
                    Helper local = new Helper();
                    local.help();
                    Runnable ref = local::help;
                  }
                  void foo() {}
                  void foo(int a) {}
                  void foo(int a, String b) {}
                }
                """, "src/I.java");
        MethodDeclaration run = r.types().get(0).methods().get(0);
        List<MethodInvocation> invocations = run.invocations();
        assertEquals(List.of("foo", "foo", "foo", "help", "foo", "help", "help"),
                invocations.stream().map(MethodInvocation::methodName).toList());
        assertEquals(0, invocations.get(0).argumentCount());
        assertEquals(1, invocations.get(1).argumentCount());
        assertEquals(2, invocations.get(2).argumentCount());
        assertTrue(invocations.get(0).unqualified());
        assertEquals("helper", invocations.get(3).receiverText());
        assertTrue(invocations.get(4).thisCall());
        assertEquals(-1, invocations.get(6).argumentCount(), "method reference arity is unknown");

        assertEquals(1, run.objectCreations().size());
        assertEquals("Helper", run.objectCreations().get(0).type());
        assertEquals(List.of("local", "ref"), run.locals().stream().map(LocalVariable::name).toList());
        assertEquals("Helper", run.locals().get(0).declaredType());
        assertEquals("Helper", run.locals().get(0).initializerType());
    }

    @Test
    void extractsNestedTypesRecordsEnumsAndAnnotations() {
        ParseResult r = parser.parse("""
                package p;
                @Service("auth")
                public class Outer {
                  @Autowired private Dep dep;
                  static class Inner {
                    class Deepest { void x() {} }
                  }
                  enum Mode { ON, OFF; boolean on() { return this == ON; } }
                  record Point(int x, int y) implements Comparable<Point> {
                    public int compareTo(Point o) { return 0; }
                  }
                  @interface Marker {}
                }
                """, "src/Outer.java");
        List<String> names = r.allTypes().stream().map(TypeDeclaration::nestedName).toList();
        assertTrue(names.containsAll(List.of("Outer", "Outer.Inner", "Outer.Inner.Deepest", "Outer.Mode",
                "Outer.Point", "Outer.Marker")), names.toString());

        TypeDeclaration outer = r.types().get(0);
        assertEquals("Service", outer.annotations().get(0).simpleName());
        assertEquals("auth", outer.annotations().get(0).firstStringArgument());
        assertEquals("Autowired", outer.fields().get(0).annotations().get(0).simpleName());

        TypeDeclaration mode = outer.nestedTypes().stream()
                .filter(t -> t.simpleName().equals("Mode")).findFirst().orElseThrow();
        assertEquals(SyntaxKind.ENUM, mode.kind());
        assertEquals(List.of("ON", "OFF"), mode.enumConstants());
        assertEquals(1, mode.methods().size());

        TypeDeclaration point = outer.nestedTypes().stream()
                .filter(t -> t.simpleName().equals("Point")).findFirst().orElseThrow();
        assertEquals(SyntaxKind.RECORD, point.kind());
        assertEquals(List.of("x", "y"), point.recordComponents().stream()
                .map(ParameterDeclaration::name).toList());
        assertEquals(List.of("Comparable<Point>"), point.interfaces());
    }

    @Test
    void everyExtractedNodeHasFileLinesNameAndKind() {
        ParseResult r = parser.parse("""
                package p;
                import java.util.List;
                class A extends B implements C {
                  int f = 1;
                  void m(String s) { helper(s); new A(); int local = 2; }
                }
                """, "src/A.java");
        List<SyntaxNode> nodes = TreeSitterParser.flatten(r);
        assertFalse(nodes.isEmpty());
        for (SyntaxNode node : allNodes(nodes)) {
            assertNotNull(node.kind(), "kind");
            assertNotNull(node.name(), "name");
            assertFalse(node.name().isBlank(), "name must not be blank: " + node);
            assertEquals("src/A.java", node.location().file());
            if (node.kind() != SyntaxKind.PACKAGE) {
                assertTrue(node.location().startLine() >= 1, "start line: " + node);
                assertTrue(node.location().endLine() >= node.location().startLine(), "end line: " + node);
            }
        }
    }

    private List<SyntaxNode> allNodes(List<SyntaxNode> nodes) {
        List<SyntaxNode> all = new java.util.ArrayList<>();
        for (SyntaxNode n : nodes) {
            all.add(n);
            all.addAll(allNodes(n.children()));
        }
        return all;
    }

    @Test
    void malformedSourceIsReportedNotThrown() {
        ParseResult r = parser.parse("class Broken { void m( { }", "src/Broken.java");
        assertTrue(r.hasSyntaxErrors());
        assertFalse(r.diagnostics().isEmpty());
    }

    @Test
    void locationsSurviveNonAsciiCharacters() {
        ParseResult r = parser.parse("""
                class Ünïcode {
                  // comment with emoji 🚀 and accents éèê
                  void méthode() { helper(); }
                }
                """, "src/U.java");
        TypeDeclaration t = r.types().get(0);
        assertEquals("Ünïcode", t.simpleName());
        MethodDeclaration m = t.methods().get(0);
        assertEquals("méthode", m.name());
        assertEquals(3, m.location().startLine());
        assertEquals("helper", m.invocations().get(0).methodName());
    }

    @Test
    void genericsAndArraysAreErasedOnlyWhenAsked() {
        assertEquals("Map", Types.erase("Map<String, List<Integer>>"));
        assertEquals("int", Types.erase("int[]"));
        assertEquals("String", Types.erase("String..."));
        assertEquals("List", Types.simpleName("java.util.List<String>"));
        assertTrue(Types.isGeneric("List<String>"));
        assertTrue(Types.isArray("byte[]"));
        assertTrue(Types.isPrimitive("boolean"));
    }
}
