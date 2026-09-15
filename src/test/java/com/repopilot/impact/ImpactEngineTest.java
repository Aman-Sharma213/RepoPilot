package com.repopilot.impact;

import com.repopilot.index.TestRepo;
import com.repopilot.symbol.SymbolId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImpactEngineTest {

    private ImpactResult analyse(TestRepo repo, String target, ImpactEngine.Options options) throws Exception {
        var index = repo.index();
        return new ImpactEngine(index.graph()).analyse(List.of(new SymbolId(target)), options);
    }

    private final TestRepo chain = TestRepo.of()
            .file("src/a/Validator.java", "package a; class Validator { boolean validate() { return true; } }")
            .file("src/a/Auth.java", """
                    package a;
                    class Auth {
                      private final Validator validator = new Validator();
                      boolean check() { return validator.validate(); }
                    }
                    """)
            .file("src/a/Login.java", """
                    package a;
                    class Login {
                      private final Auth auth = new Auth();
                      boolean login() { return auth.check(); }
                    }
                    """)
            .file("src/test/java/a/LoginTest.java", """
                    package a;
                    class LoginTest {
                      void shouldLogin() { new Login().login(); }
                    }
                    """);

    @Test
    void radiusReflectsShortestDependencyPath() throws Exception {
        ImpactResult result = analyse(chain, "a.Validator#validate()", ImpactEngine.Options.defaults());
        assertEquals(1, radiusOf(result, "a.Auth#check()"));
        assertEquals(2, radiusOf(result, "a.Login#login()"));
        assertEquals(3, radiusOf(result, "a.LoginTest#shouldLogin()"));
        assertEquals(ImpactKind.DIRECT, kindOf(result, "a.Validator#validate()"));
        assertEquals(ImpactKind.INDIRECT, kindOf(result, "a.Auth#check()"));
        assertEquals(ImpactKind.TEST_IMPACT, kindOf(result, "a.LoginTest#shouldLogin()"));
    }

    @Test
    void radiusLimitIsRespected() throws Exception {
        ImpactResult result = analyse(chain, "a.Validator#validate()",
                ImpactEngine.Options.defaults().withMaxRadius(1));
        assertTrue(result.affected().stream().allMatch(a -> a.radius() <= 1));
        assertTrue(result.notes().stream().anyMatch(n -> n.contains("traversal stopped at radius 1")));
    }

    @Test
    void testsCanBeExcluded() throws Exception {
        ImpactResult result = analyse(chain, "a.Validator#validate()",
                new ImpactEngine.Options(5, false, true, 0));
        assertTrue(result.affected().stream().noneMatch(a -> a.file().contains("src/test/")));
    }

    @Test
    void everyAffectedSymbolCarriesItsEvidencePath() throws Exception {
        ImpactResult result = analyse(chain, "a.Validator#validate()", ImpactEngine.Options.defaults());
        for (AffectedSymbol affected : result.affected()) {
            if (affected.radius() == 0) {
                assertTrue(affected.path().isEmpty());
                continue;
            }
            assertEquals(affected.radius(), affected.path().size());
            assertEquals("a.Validator#validate()", affected.target().value());
            assertFalse(affected.reason().isBlank());
            assertTrue(affected.confidence() > 0 && affected.confidence() <= 1);
            String explanation = new ImpactExplainer().explain(affected);
            assertTrue(explanation.contains("changed target: Validator.validate()"), explanation);
        }
    }

    @Test
    void ambiguousEdgesProduceUncertainImpact() throws Exception {
        TestRepo repo = TestRepo.of()
                .file("src/a/Over.java", """
                        package a;
                        class Over {
                          void take(int a) {}
                          void take(String a) {}
                        }
                        """)
                .file("src/a/Caller.java", """
                        package a;
                        class Caller {
                          private final Over over = new Over();
                          void go(Unknown unknown) { over.take(unknown.value()); }
                        }
                        """);
        ImpactResult result = analyse(repo, "a.Over#take(int)", ImpactEngine.Options.defaults());
        assertEquals(ImpactKind.UNCERTAIN, kindOf(result, "a.Caller#go(Unknown)"));
        assertTrue(result.ofKind(ImpactKind.UNCERTAIN).get(0).reason().contains("uncertain"));
    }

    @Test
    void overridesPropagateThroughTheDeclaredType() throws Exception {
        TestRepo repo = TestRepo.of()
                .file("src/a/Api.java", "package a; interface Api { void run(); }")
                .file("src/a/Impl.java", "package a; class Impl implements Api { public void run() {} }")
                .file("src/a/Client.java", """
                        package a;
                        class Client {
                          private final Api api = new Impl();
                          void go() { api.run(); }
                        }
                        """);
        ImpactResult viaInterface = analyse(repo, "a.Api#run()", ImpactEngine.Options.defaults());
        assertEquals(1, radiusOf(viaInterface, "a.Impl#run()"));
        assertEquals(1, radiusOf(viaInterface, "a.Client#go()"));

        ImpactResult viaImplementation = analyse(repo, "a.Impl#run()", ImpactEngine.Options.defaults());
        assertEquals(1, radiusOf(viaImplementation, "a.Api#run()"), "the implemented contract is affected");
        assertEquals(2, radiusOf(viaImplementation, "a.Client#go()"), "callers use the interface");
    }

    @Test
    void cyclesTerminate() throws Exception {
        TestRepo repo = TestRepo.of().file("src/a/Cycle.java", """
                package a;
                class Cycle {
                  void a() { b(); }
                  void b() { a(); }
                }
                """);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            ImpactResult result = analyse(repo, "a.Cycle#a()", ImpactEngine.Options.defaults());
            assertEquals(1, radiusOf(result, "a.Cycle#b()"));
        });
    }

    @Test
    void unknownTargetIsReportedNotInvented() throws Exception {
        ImpactResult result = analyse(chain, "a.DoesNotExist#nope()", ImpactEngine.Options.defaults());
        assertTrue(result.targets().isEmpty());
        assertTrue(result.affected().isEmpty());
        assertTrue(result.notes().stream().anyMatch(n -> n.contains("not part of the index")));
    }

    @Test
    void invalidOptionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ImpactEngine.Options(-1, true, true, 0));
        assertThrows(IllegalArgumentException.class, () -> new ImpactEngine.Options(1, true, true, -0.1));
        assertThrows(IllegalArgumentException.class, () -> new ImpactEngine.Options(1, true, true, 1.1));
        assertThrows(IllegalArgumentException.class,
                () -> new ImpactEngine.Options(1, true, true, Double.NaN));
        assertThrows(NullPointerException.class, () -> new ImpactEngine(null));
    }

    @Test
    void duplicateTargetsAreCollapsedDeterministically() throws Exception {
        var index = chain.index();
        SymbolId target = new SymbolId("a.Validator#validate()");
        ImpactResult result = new ImpactEngine(index.graph()).analyse(List.of(target, target),
                ImpactEngine.Options.defaults());
        assertEquals(List.of(target), result.targets());
        assertEquals(1, result.affected().stream().filter(a -> a.symbol().equals(target)).count());
    }

    @Test
    void radiusNoteIsOnlyAddedWhenResultsWereActuallyTruncated() throws Exception {
        TestRepo leaf = TestRepo.of().file("src/a/Leaf.java", "package a; class Leaf { void done() {} }");
        ImpactResult result = analyse(leaf, "a.Leaf#done()",
                ImpactEngine.Options.defaults().withMaxRadius(1));
        assertTrue(result.notes().stream().noneMatch(n -> n.contains("traversal stopped")));
    }

    private int radiusOf(ImpactResult result, String symbol) {
        return result.affected().stream().filter(a -> a.symbol().value().equals(symbol))
                .findFirst().map(AffectedSymbol::radius)
                .orElseThrow(() -> new AssertionError(symbol + " is not in the impact set: "
                        + result.affected().stream().map(a -> a.symbol().value()).toList()));
    }

    private ImpactKind kindOf(ImpactResult result, String symbol) {
        return result.affected().stream().filter(a -> a.symbol().value().equals(symbol))
                .findFirst().map(AffectedSymbol::kind).orElseThrow();
    }
}
