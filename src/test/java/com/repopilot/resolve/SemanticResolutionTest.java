package com.repopilot.resolve;

import com.repopilot.graph.EdgeType;
import com.repopilot.index.TestRepo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SemanticResolutionTest {

    private Optional<Reference> call(List<Reference> references, String fromContains, String rawContains) {
        return references.stream()
                .filter(r -> r.type() == EdgeType.CALLS)
                .filter(r -> r.source().value().contains(fromContains))
                .filter(r -> r.rawText().contains(rawContains))
                .findFirst();
    }

    private String target(Reference reference) {
        return reference.resolution().candidates().get(0).value();
    }

    @Test
    void level1_sameClassCall() {
        var refs = TestRepo.of().file("a/A.java", """
                package a;
                class A {
                  void caller() { helper(); }
                  void helper() {}
                }
                """).references();
        Reference r = call(refs, "caller", "helper").orElseThrow();
        assertEquals(ResolutionStatus.RESOLVED, r.resolution().status());
        assertEquals("a.A#helper()", target(r));
        assertEquals(ResolutionLevel.L1_SAME_CLASS, r.resolution().level());
    }

    @Test
    void level2_explicitImportAndLevel3_samePackage() {
        var refs = TestRepo.of()
                .file("a/Caller.java", """
                        package a;
                        import b.Imported;
                        class Caller {
                          void go() {
                            Imported imported = new Imported();
                            imported.run();
                            Neighbour neighbour = new Neighbour();
                            neighbour.ping();
                          }
                        }
                        """)
                .file("a/Neighbour.java", "package a; class Neighbour { void ping() {} }")
                .file("b/Imported.java", "package b; public class Imported { public void run() {} }")
                .references();
        assertEquals("b.Imported#run()", target(call(refs, "Caller#go", "imported.run").orElseThrow()));
        assertEquals("a.Neighbour#ping()", target(call(refs, "Caller#go", "neighbour.ping").orElseThrow()));
    }

    @Test
    void level4_fieldTypeAndLevel5_parameterType() {
        var refs = TestRepo.of()
                .file("a/S.java", """
                        package a;
                        class S {
                          private final Dep dep = new Dep();
                          void viaField() { dep.work(); }
                          void viaParameter(Dep parameter) { parameter.work(); }
                          void viaLocal() { Dep local = new Dep(); local.work(); }
                        }
                        """)
                .file("a/Dep.java", "package a; class Dep { void work() {} }")
                .references();
        Reference field = call(refs, "viaField", "dep.work").orElseThrow();
        assertEquals("a.Dep#work()", target(field));
        assertEquals(ResolutionLevel.L1_SAME_CLASS, field.resolution().level(), "call level is where the method lives");
        assertEquals("a.Dep#work()", target(call(refs, "viaParameter", "parameter.work").orElseThrow()));
        assertEquals("a.Dep#work()", target(call(refs, "viaLocal", "local.work").orElseThrow()));
    }

    @Test
    void level6_chainedCallReturnType() {
        var refs = TestRepo.of()
                .file("a/Chain.java", """
                        package a;
                        class Chain {
                          private Factory factory;
                          void go() { factory.create().work(); }
                        }
                        """)
                .file("a/Factory.java", "package a; class Factory { Dep create() { return new Dep(); } }")
                .file("a/Dep.java", "package a; class Dep { void work() {} }")
                .references();
        Reference chained = call(refs, "Chain#go", "factory.create().work").orElseThrow();
        assertEquals(ResolutionStatus.RESOLVED, chained.resolution().status());
        assertEquals("a.Dep#work()", target(chained));
        assertTrue(chained.resolution().reason().contains("returns Dep"));
    }

    @Test
    void level7_inheritanceAndLevel8_interfaces() {
        var refs = TestRepo.of()
                .file("a/Base.java", "package a; class Base { void inherited() {} }")
                .file("a/Child.java", """
                        package a;
                        class Child extends Base implements Iface {
                          public void fromInterface() {}
                          void go() { inherited(); super.inherited(); }
                        }
                        """)
                .file("a/Iface.java", "package a; interface Iface { void fromInterface(); }")
                .file("a/Client.java", """
                        package a;
                        class Client {
                          void call(Iface iface) { iface.fromInterface(); }
                        }
                        """)
                .references();
        Reference inherited = call(refs, "Child#go", "inherited(0 args)").orElseThrow();
        assertEquals("a.Base#inherited()", target(inherited));
        assertEquals(ResolutionLevel.L7_INHERITANCE, inherited.resolution().level());
        Reference viaInterface = call(refs, "Client#call", "iface.fromInterface").orElseThrow();
        assertEquals("a.Iface#fromInterface()", target(viaInterface));
        assertEquals(ResolutionLevel.L1_SAME_CLASS, viaInterface.resolution().level());

        Reference override = refs.stream().filter(r -> r.type() == EdgeType.OVERRIDES).findFirst().orElseThrow();
        assertEquals("a.Iface#fromInterface()", target(override));
    }

    @Test
    void level9_overloadsSelectedByArityAndArgumentTypes() {
        var refs = TestRepo.of()
                .file("a/O.java", """
                        package a;
                        class O {
                          void take(int a) {}
                          void take(String a) {}
                          void take(int a, int b) {}
                          void go(Unknown unknown) {
                            take(1);
                            take("text");
                            take(1, 2);
                            take(unknown.value());
                          }
                        }
                        """).references();
        assertEquals("a.O#take(int)", target(call(refs, "O#go", "take(1 args)").stream()
                .findFirst().orElseThrow()));
        List<Reference> singleArg = refs.stream()
                .filter(r -> r.type() == EdgeType.CALLS && r.rawText().equals("take(1 args)")).toList();
        assertEquals(3, singleArg.size(), "take(1), take(\"text\") and take(unknown.value())");
        assertTrue(singleArg.stream().anyMatch(r -> r.resolution().status() == ResolutionStatus.AMBIGUOUS),
                "an argument of unknown type cannot select an overload");
        Reference ambiguous = singleArg.stream()
                .filter(r -> r.resolution().status() == ResolutionStatus.AMBIGUOUS).findFirst().orElseThrow();
        assertEquals(2, ambiguous.resolution().candidates().size());
        assertTrue(ambiguous.resolution().reason().contains("not statically decidable"));
    }

    @Test
    void level10_genericsResolveOnlyThroughDeclaredBounds() {
        var refs = TestRepo.of()
                .file("a/Box.java", """
                        package a;
                        class Box<T> {
                          private T item;
                          void use() { item.toString(); }
                        }
                        """)
                .references();
        Reference unresolved = refs.stream()
                .filter(r -> r.type() == EdgeType.CALLS && r.rawText().contains("item.toString"))
                .findFirst().orElseThrow();
        assertEquals(ResolutionStatus.UNRESOLVED, unresolved.resolution().status());
        assertTrue(unresolved.resolution().reason().contains("type variable")
                        || unresolved.resolution().reason().contains("outside the indexed sources"),
                unresolved.resolution().reason());
    }

    @Test
    void unresolvedAmbiguousAndDynamicCasesAreExplicit() {
        var refs = TestRepo.of()
                .file("a/M.java", """
                        package a;
                        import java.util.List;
                        class M {
                          void go(List<String> list, java.lang.reflect.Method method) throws Exception {
                            list.size();
                            method.invoke(null);
                          }
                        }
                        """).references();
        List<Reference> calls = refs.stream().filter(r -> r.type() == EdgeType.CALLS).toList();
        assertFalse(calls.isEmpty());
        assertTrue(calls.stream().allMatch(r -> r.resolution().status() == ResolutionStatus.UNRESOLVED));
        assertTrue(calls.stream().allMatch(r -> r.resolution().reason().contains("outside the indexed sources")
                || r.resolution().reason().contains("unresolved")));
    }

    @Test
    void sameSimpleNameInTwoPackagesIsAmbiguousWithoutImport() {
        var resolver = TestRepo.of()
                .file("p1/Service.java", "package p1; public class Service { public void call() {} }")
                .file("p2/Service.java", "package p2; public class Service { public void call() {} }")
                .file("p3/User.java", """
                        package p3;
                        class User {
                          void go(Service service) { service.call(); }
                        }
                        """).references();
        Reference call = resolver.stream()
                .filter(r -> r.type() == EdgeType.CALLS && r.rawText().contains("service.call"))
                .findFirst().orElseThrow();
        assertNotEquals(ResolutionStatus.RESOLVED, call.resolution().status());
    }

    @Test
    void importedNameWinsOverAmbiguity() {
        var refs = TestRepo.of()
                .file("p1/Service.java", "package p1; public class Service { public void call() {} }")
                .file("p2/Service.java", "package p2; public class Service { public void call() {} }")
                .file("p3/User.java", """
                        package p3;
                        import p2.Service;
                        class User {
                          void go(Service service) { service.call(); }
                        }
                        """).references();
        Reference call = refs.stream()
                .filter(r -> r.type() == EdgeType.CALLS && r.rawText().contains("service.call"))
                .findFirst().orElseThrow();
        assertEquals(ResolutionStatus.RESOLVED, call.resolution().status());
        assertEquals("p2.Service#call()", target(call));
    }

    @Test
    void staticCallsOnTypeNamesResolve() {
        var refs = TestRepo.of()
                .file("a/Util.java", "package a; class Util { static String help() { return \"\"; } }")
                .file("a/Client.java", "package a; class Client { void go() { Util.help(); } }")
                .references();
        assertEquals("a.Util#help()", target(call(refs, "Client#go", "Util.help").orElseThrow()));
    }

    @Test
    void inheritanceCyclesDoNotHang() {
        var repo = TestRepo.of()
                .file("a/A.java", "package a; class A extends B { }")
                .file("a/B.java", "package a; class B extends A { void m() {} }");
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            var resolver = repo.resolver();
            assertTrue(resolver.inheritance().supertypeClosure("a.A").contains("a.B"));
        });
    }
}
