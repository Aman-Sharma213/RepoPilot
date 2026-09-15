package com.repopilot.symbol;

import com.repopilot.parse.ParseResult;
import com.repopilot.parse.TreeSitterParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SymbolIndexTest {

    private final TreeSitterParser parser = new TreeSitterParser();
    private final SymbolExtractor extractor = new SymbolExtractor();

    private SymbolIndex indexOf(String... sources) {
        SymbolIndex index = new SymbolIndex();
        int i = 0;
        for (String source : sources) {
            ParseResult r = parser.parse(source, "src/File" + i++ + ".java");
            index.addAll(extractor.extract(r));
        }
        return index;
    }

    @Test
    void symbolIdsFollowTheDocumentedShape() {
        SymbolIndex index = indexOf("""
                package com.example.auth;
                class AuthService {
                  private String secret;
                  AuthService(Token t) {}
                  boolean validate(Token token) { return true; }
                }
                """);
        assertTrue(index.contains(SymbolId.ofType("com.example.auth.AuthService")));
        assertTrue(index.contains(new SymbolId("com.example.auth.AuthService#validate(Token)")));
        assertTrue(index.contains(new SymbolId("com.example.auth.AuthService#<init>(Token)")));
        assertTrue(index.contains(new SymbolId("com.example.auth.AuthService#secret")));
        assertTrue(index.contains(new SymbolId("com.example.auth.AuthService#validate(Token)::token")));

        SymbolId method = new SymbolId("com.example.auth.AuthService#validate(Token)");
        assertEquals("com.example.auth.AuthService", method.ownerFqn());
        assertEquals("validate", method.memberName());
        assertEquals(List.of("Token"), method.parameterTypes());
        assertEquals("AuthService.validate(Token)", method.display());
        assertEquals("AuthService.AuthService(Token)",
                new SymbolId("com.example.auth.AuthService#<init>(Token)").display());
    }

    @Test
    void sameMethodNameInDifferentClassesStaysDistinct() {
        SymbolIndex index = indexOf("""
                package a;
                class One { void run() {} }
                class Two { void run() {} }
                """);
        assertEquals(1, index.methodsOf("a.One", "run").size());
        assertEquals(1, index.methodsOf("a.Two", "run").size());
        assertNotEquals(index.methodsOf("a.One", "run").get(0).id(), index.methodsOf("a.Two", "run").get(0).id());
    }

    @Test
    void overloadsAreDistinctSymbols() {
        SymbolIndex index = indexOf("""
                package a;
                class Over {
                  void go() {}
                  void go(int a) {}
                  void go(int a, String b) {}
                  void go(java.util.List<String> items) {}
                  void go(int... many) {}
                  void go(int[] array, int extra) {}
                }
                """);
        List<String> ids = index.methodsOf("a.Over", "go").stream().map(s -> s.id().value()).toList();
        assertEquals(6, ids.size(), ids.toString());
        assertTrue(ids.contains("a.Over#go()"));
        assertTrue(ids.contains("a.Over#go(int)"));
        assertTrue(ids.contains("a.Over#go(int,String)"));
        assertTrue(ids.contains("a.Over#go(java.util.List)"));
        assertTrue(ids.contains("a.Over#go(int[])"), ids.toString());
        assertTrue(ids.contains("a.Over#go(int[],int)"), ids.toString());
    }

    @Test
    void nestedClassesUseDottedOwners() {
        SymbolIndex index = indexOf("""
                package a.b;
                class Outer {
                  static class Inner { void deep() {} }
                }
                """);
        assertTrue(index.type("a.b.Outer.Inner").isPresent());
        assertEquals("a.b.Outer", index.type("a.b.Outer.Inner").orElseThrow().ownerFqn());
        assertEquals(1, index.methodsOf("a.b.Outer.Inner", "deep").size());
    }

    @Test
    void identicalSimpleNamesInDifferentPackagesAreAmbiguousByName() {
        SymbolIndex index = indexOf(
                "package p1; public class Service { void call() {} }",
                "package p2; public class Service { void call() {} }");
        assertEquals(2, index.typesNamed("Service").size());
        assertEquals(List.of("p1.Service", "p2.Service"),
                index.typesNamed("Service").stream().map(s -> s.id().value()).toList());
    }

    @Test
    void recordsEnumsAndInterfacesAreIndexedWithTheRightKind() {
        SymbolIndex index = indexOf("""
                package a;
                interface Iface { void x(); }
                enum Mode { ON, OFF }
                record Point(int x, int y) {}
                @interface Marker {}
                """);
        assertEquals(SymbolKind.INTERFACE, index.type("a.Iface").orElseThrow().kind());
        assertEquals(SymbolKind.ENUM, index.type("a.Mode").orElseThrow().kind());
        assertEquals(SymbolKind.RECORD, index.type("a.Point").orElseThrow().kind());
        assertEquals(SymbolKind.ANNOTATION_TYPE, index.type("a.Marker").orElseThrow().kind());
        assertTrue(index.fieldOf("a.Point", "x").isPresent(), "record components behave like fields");
    }

    @Test
    void supertypesAndAnnotationsArePreservedAsWritten() {
        SymbolIndex index = indexOf("""
                package a;
                @Service
                class Impl extends Base implements Iface, Other { }
                """);
        Symbol impl = index.type("a.Impl").orElseThrow();
        assertEquals(List.of("Base", "Iface", "Other"), impl.superTypes());
        assertTrue(impl.hasAnnotation("Service"));
    }

    @Test
    void removingAFileDropsItsSymbolsFromEveryLookup() {
        SymbolIndex index = indexOf("package a; class Gone { void m() {} }");
        assertEquals(1, index.typesNamed("Gone").size());
        index.removeFile("src/File0.java");
        assertEquals(0, index.size());
        assertTrue(index.typesNamed("Gone").isEmpty());
        assertTrue(index.type("a.Gone").isEmpty());
        assertTrue(index.methodsOf("a.Gone", "m").isEmpty());
    }

    @Test
    void blankSymbolIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SymbolId(" "));
    }
}
