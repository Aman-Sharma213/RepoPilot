# Phase log

The project was built in the prescribed order: build → test → verify → lock → next phase. Each
phase compiled and its tests passed before the next one started.

| Phase | Deliverable | Where | Verification |
| --- | --- | --- | --- |
| 0 | Architecture contract | `README.md` | CST / symbols / semantics / graph are separate packages |
| 1 | CLI foundation | `cli/` | `CliTest`: help, version, unknown command/option, exit codes |
| 2 | Repository scanner | `scan/` | `RepositoryScannerTest`: synthetic repo, ignored dirs, generated/binary/oversized/symlink |
| 3 | Tree-sitter parsing | `parse/` | `TreeSitterParserTest`: types, members, invocations, records, enums, generics, non-ASCII, malformed |
| 4 | Symbol index | `symbol/` | `SymbolIndexTest`: id shape, overloads, arrays/varargs, nested, cross-package name clashes |
| 5 | Semantic resolution | `resolve/` | `SemanticResolutionTest`: one test per level 1-10 plus ambiguity/unresolved/cycles |
| 6 | Code graph | `graph/` | evidence on every edge; digest-based determinism tests |
| 7 | Persistence | `persist/` | `IndexStoreTest`: round trip, corrupt files, version mismatch, clean |
| 8 | Incremental indexing | `incremental/` | `IncrementalIndexingTest`: 1 of 3 files reparsed, renames, digest equality |
| 9 | Concurrency | `concurrent/` | `ConcurrencyTest`: identical digests across strategies, input ordering, bounded concurrency, isolation, cancellation |
| 10 | Deterministic PR diff | `git/`, `github/` | `DiffAnalysisTest`: hunks, added/deleted/renamed/binary, line→symbol mapping |
| 11 | NL target resolution | `ai/` | `AiTargetResolutionTest`: retrieval, hallucination rejection, malformed JSON, threshold, fallback |
| 12 | Impact engine | `impact/` | `ImpactEngineTest`: radius, cycles, unknown targets |
| 13 | Classification | `impact/ImpactClassifier` | DIRECT / INDIRECT / UNCERTAIN / TEST_IMPACT tests |
| 14 | Explainability | `impact/ImpactExplainer` | evidence-path assertions and `--explain` output test |
| 15 | Spring analysis | `spring/` | `SpringAnalysisTest`: field/constructor injection, `@Qualifier`, `@Primary`, `@Bean`, `@EventListener`, no-Spring baseline |
| 16 | CLI UX | `cli/Ui` | `CliEndToEndTest` asserts the documented layout |
| 17 | Failure engineering | — | `FailureEngineeringTest`: empty/huge repos, malformed Java, broken imports, reflection, deleted files, corrupt index, interrupted writes, network failure |
| 18 | Benchmark | `benchmark/` | `repo-pilot benchmark`: P/R/F1, timings, memory, strategy comparison |
| 19 | Security | `security/` | `SecurityTest`: path traversal, symlink escape, huge files, prompt injection |
| 20 | Final integration | `index/IndexingService` | end-to-end CLI workflow test and manual demo run |
