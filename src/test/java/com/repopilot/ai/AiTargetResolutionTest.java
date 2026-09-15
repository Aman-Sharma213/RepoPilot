package com.repopilot.ai;

import com.repopilot.index.TestRepo;
import com.repopilot.symbol.SymbolIndex;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiTargetResolutionTest {

    private final SymbolIndex index = TestRepo.of()
            .file("src/a/AuthTokenValidator.java", """
                    package a;
                    class AuthTokenValidator {
                      boolean validate(Token token) { return true; }
                    }
                    """)
            .file("src/a/JwtValidator.java", """
                    package a;
                    class JwtValidator {
                      boolean validate(Token token) { return true; }
                    }
                    """)
            .file("src/a/Token.java", "package a; class Token {}")
            .file("src/a/Unrelated.java", "package a; class Unrelated { void render() {} }")
            .symbols();

    @Test
    void retrievalOnlyReturnsRealSymbols() {
        List<TargetCandidate> candidates = new CandidateRetriever(index)
                .retrieve("Refactor AuthToken validation to accept JWT v2", 10);
        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream().allMatch(c -> index.contains(c.symbol())));
        assertTrue(candidates.stream().anyMatch(c -> c.symbol().value().startsWith("a.AuthTokenValidator")));
        assertTrue(candidates.stream().noneMatch(c -> c.symbol().value().contains("Unrelated")));
    }

    @Test
    void tokenizerSplitsCamelCase() {
        assertEquals(List.of("auth", "token", "validator"), CandidateRetriever.tokenize("AuthTokenValidator"));
        assertEquals(List.of("jwt", "validation"), CandidateRetriever.tokenize("JWT validation"));
    }

    @Test
    void withoutApiKeyRankingIsDeterministicAndLabelled() {
        TargetResolution resolution = new NlTargetResolver(index, new GroqClient(null, "m", "http://unused", Duration.ofSeconds(1)))
                .resolve("AuthTokenValidator validate", 0.3);
        assertEquals("deterministic", resolution.source());
        assertFalse(resolution.ambiguous());
        assertTrue(resolution.notes().stream().anyMatch(n -> n.contains("AI ranking disabled")));
        TargetResolution again = new NlTargetResolver(index, null).resolve("AuthTokenValidator validate", 0.3);
        assertEquals(resolution.targets(), again.targets(), "ranking must be reproducible");
    }

    @Test
    void lowConfidenceIsReportedAsAmbiguousNotGuessed() {
        TargetResolution resolution = new NlTargetResolver(index, null)
                .resolve("validate something", 0.95);
        assertTrue(resolution.ambiguous());
        assertTrue(resolution.targets().isEmpty());
        assertFalse(resolution.alternatives().isEmpty());
    }

    @Test
    void noMatchIsReportedExplicitly() {
        TargetResolution resolution = new NlTargetResolver(index, null).resolve("kubernetes helm chart", 0.5);
        assertTrue(resolution.ambiguous());
        assertTrue(resolution.notes().get(0).contains("no indexed symbol matches"));
    }

    @Test
    void hallucinatedSymbolsAreRejected() {
        var offered = new CandidateRetriever(index).retrieve("AuthTokenValidator validate", 5);
        AiResponseValidator validator = new AiResponseValidator(index);
        String response = """
                {"targets":[
                  {"symbol":"a.AuthTokenValidator#validate(Token)","confidence":0.94,"reason":"matches"},
                  {"symbol":"com.invented.Nope#gone()","confidence":0.99,"reason":"hallucination"}
                ]}
                """;
        var validated = validator.validate(response, offered);
        assertEquals(1, validated.targets().size());
        assertEquals("a.AuthTokenValidator#validate(Token)", validated.targets().get(0).symbol().value());
        assertTrue(validated.notes().get(0).contains("hallucinated"));
    }

    @Test
    void malformedOrEmptyAiResponsesFallBackInsteadOfCrashing() {
        var offered = new CandidateRetriever(index).retrieve("AuthTokenValidator", 5);
        AiResponseValidator validator = new AiResponseValidator(index);
        assertThrows(AiUnavailableException.class, () -> validator.validate("not json", offered));
        assertThrows(AiUnavailableException.class, () -> validator.validate("{\"foo\":1}", offered));
        assertThrows(AiUnavailableException.class,
                () -> validator.validate("{\"targets\":[]}", offered));
        assertThrows(AiUnavailableException.class, () -> validator.validate(
                "{\"targets\":[{\"symbol\":\"a.Token\",\"confidence\":\"high\"}]}", offered));
    }

    @Test
    void confidenceOutsideUnitRangeIsRejected() {
        var offered = new CandidateRetriever(index).retrieve("AuthTokenValidator validate", 5);
        assertThrows(AiUnavailableException.class, () -> new AiResponseValidator(index).validate(
                "{\"targets\":[{\"symbol\":\"a.AuthTokenValidator#validate(Token)\",\"confidence\":7.5}]}",
                offered));
    }

    @Test
    void validatorCapsModelSelectionAndOrdersTiesDeterministically() {
        var offered = new CandidateRetriever(index).retrieve("validator token validate", 25);
        String entries = offered.stream().map(c -> "{\"symbol\":\"" + c.symbol().value()
                + "\",\"confidence\":0.9}").collect(java.util.stream.Collectors.joining(","));
        var validated = new AiResponseValidator(index).validate("{\"targets\":[" + entries + "]}", offered);
        assertTrue(validated.targets().size() <= AiResponseValidator.MAX_TARGETS);
        assertEquals(validated.targets().stream().map(RankedTarget::symbol).sorted().toList(),
                validated.targets().stream().map(RankedTarget::symbol).toList());
    }

    @Test
    void resolverRejectsInvalidThresholds() {
        NlTargetResolver resolver = new NlTargetResolver(index, null);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("validator", -0.1));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("validator", Double.NaN));
    }

    @Test
    void groqFailureFallsBackToDeterministicRanking() {
        GroqClient broken = new GroqClient("test-key", "model", "http://127.0.0.1:1/none",
                Duration.ofMillis(300));
        TargetResolution resolution = new NlTargetResolver(index, broken)
                .resolve("AuthTokenValidator validate", 0.3);
        assertEquals("deterministic", resolution.source());
        assertTrue(resolution.notes().stream().anyMatch(n -> n.contains("AI ranking unavailable")));
        assertFalse(resolution.targets().isEmpty());
    }
}
