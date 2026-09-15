# Repo Pilot

Evidence-backed contribution planning and change-impact analysis for Java repositories.

Repo Pilot helps an open-source contributor turn an issue or proposed change into a verified
starting point, reading path, impact set, and relevant tests. It indexes a Java repository into a
semantic code graph and answers two questions:

> Where should I start, and if I change this, what else may be affected — and *why*?

Every answer comes with an evidence trail (file, line, edge type, resolution level, confidence).
Nothing in the graph is guessed, and anything that cannot be decided statically is reported as
`AMBIGUOUS`, `UNRESOLVED` or `DYNAMIC` instead of being silently dropped or invented.

```
                    REPO PILOT
                        │
        ┌───────────────┼────────────────┐
        ▼               ▼                ▼
   Git / Files      Tree-sitter         CLI
        │               │                │
        └───────┬───────┘                │
                ▼                        │
        Syntax / Structure               │
                ▼                        │
        Symbol Extraction                │
                ▼                        │
        Semantic Resolution              │
                ▼                        │
          Code Graph                     │
                ▼                        │
        Impact Analysis ◄──── AI ────────┘
                │
                ▼
       Explainable Results
```

## Beginner setup guides

Start with [`START_HERE.md`](START_HERE.md). Detailed guides: [`LOCAL_SETUP`](docs/LOCAL_SETUP.md), [`GROQ_SETUP`](docs/GROQ_SETUP.md), [`PR_AND_DIFF_GUIDE`](docs/PR_AND_DIFF_GUIDE.md), [`IDE_GUIDE`](docs/IDE_GUIDE.md), [`TESTING_AND_TROUBLESHOOTING`](docs/TESTING_AND_TROUBLESHOOTING.md), and the [`AUDIT_REPORT`](docs/AUDIT_REPORT.md).

## Quick start

```bash
mvn package                      # builds target/repo-pilot.jar (requires JDK 21+)
cd /path/to/a/java/repo
/path/to/repo-pilot/bin/repo-pilot init
/path/to/repo-pilot/bin/repo-pilot index
/path/to/repo-pilot/bin/repo-pilot impact "AuthTokenValidator.validate" --explain
```

### Commands

| Command | Purpose |
| --- | --- |
| `repo-pilot init` | Create the `.repo-pilot/` workspace (and add it to `.gitignore`). |
| `repo-pilot index [--full] [--threads N] [--strategy SINGLE_THREAD\|FIXED_POOL\|VIRTUAL_THREADS] [--no-spring]` | Scan, parse, resolve and persist the code graph. Incremental by default. |
| `repo-pilot status` | Repository scale, project coverage, external gaps, edge counts, and diagnostics. |
| `repo-pilot impact <symbol> [--radius N] [--no-tests] [--explain] [--show-unresolved]` | Reverse dependency analysis for one symbol. |
| `repo-pilot pr --org O --repo R --pr N` / `--diff-file f.diff` / `--local` | Deterministic diff-driven impact analysis. |
| `repo-pilot plan "<description>" [--mode local\|ai\|auto] [--threshold 0.8]` | Produce a guided contribution plan (`diff` remains an alias). |
| `repo-pilot benchmark [--dir benchmarks] [--strategies]` | Precision / recall / F1 against ground-truth cases. |
| `repo-pilot clean` | Delete the index. |

Exit codes: `0` ok, `1` runtime error, `2` usage error, `3` ambiguous target, `4` not indexed.

Environment: `GITHUB_TOKEN` (private repos and higher rate limits), `GROQ_API_KEY` and optional
`GROQ_MODEL`. `--mode local` never calls AI; `--mode ai` requires the user's key; `--mode auto`
uses Groq when configured and otherwise falls back locally. `--groq-key-env NAME` selects another
environment-variable name without exposing or persisting the secret.

## Architecture contract (Phase 0)

The pipeline separates four things that are frequently and wrongly conflated:

1. **CST parsing** (`parse`) — Tree-sitter produces a concrete syntax tree. Types here are raw
   source text. Tree-sitter provides *no* type information, and this project never claims otherwise.
2. **Symbol extraction** (`symbol`) — declarations become stably identified symbols.
3. **Semantic resolution** (`resolve`) — imports, field/parameter/local types, chained-call return
   types, inheritance, interfaces, overloads and generic bounds decide what a reference points to.
4. **Code graph construction** (`graph`) — resolved references become typed edges carrying evidence.

### Package structure

```
com.repopilot
├── cli            Picocli commands and console rendering
├── scan           repository scanning and file filtering        (Phase 2)
├── parse          Tree-sitter parsing and syntax extraction     (Phase 3)
├── symbol         symbol identity and symbol index              (Phase 4)
├── resolve        type / import / method / inheritance resolvers (Phase 5)
├── graph          code graph, edges, evidence                   (Phase 6)
├── persist        .repo-pilot on-disk index                     (Phase 7)
├── incremental    change detection and parse cache              (Phase 8)
├── concurrent     bounded, deterministic indexing executor      (Phase 9)
├── git            unified-diff parsing, JGit access             (Phase 10)
├── github         pull-request fetching                         (Phase 10)
├── ai             candidate retrieval, Groq ranking, validation  (Phase 11)
├── impact         reverse traversal, classification, explaining (Phases 12-14)
├── spring         Spring DI / bean / event analysis             (Phase 15)
├── benchmark      precision/recall harness                      (Phase 18)
├── security       path safety and resource limits               (Phase 19)
└── index          the orchestrating pipeline
```

### Data model

* `SymbolId` — stable textual identity:
  * type `com.example.auth.AuthService`, nested type `…AuthService.Inner`
  * method `com.example.auth.AuthService#validate(Token)`
  * constructor `…AuthService#<init>(Token)`
  * field `…AuthService#secret`, parameter `…#validate(Token)::token`
  * parameter types are the *erased types as written* (array dimensions preserved, varargs treated
    as arrays), so overloads never collide and identity is computable from syntax alone.
* `Symbol` — kind, names, owner, parameter types, declared/return type, raw supertypes, modifiers,
  annotations, type parameters, source location.
* `SourceLocation` — file, 1-based line/column range, UTF-8 byte range.

### Graph model

Nodes are always real indexed symbols. Edge types:
`CALLS`, `EXTENDS`, `IMPLEMENTS`, `OVERRIDES`, `FIELD_TYPE`, `PARAMETER_TYPE`, `RETURN_TYPE`,
`INSTANTIATES`, `IMPORTS`, `ANNOTATED_WITH`, plus the framework-specific `SPRING_DI`,
`EVENT_LISTENER`, `BEAN_CREATION`, and `DYNAMIC`.

Every edge carries `sourceLocation`, `confidence`, `status` (`RESOLVED` / `AMBIGUOUS`),
`resolutionLevel` and human-readable `evidence`. Ambiguous references produce one edge per
candidate, all flagged, which is what makes `UNCERTAIN` impact possible.

### Resolution levels actually implemented

| Level | Capability | Status |
| --- | --- | --- |
| 1 | same-class calls | implemented |
| 2 | explicit imports (incl. fully qualified names, on-demand imports) | implemented |
| 3 | same-package types | implemented |
| 4 | field types (incl. inherited fields and field-access chains) | implemented |
| 5 | parameter and local-variable types (incl. `var` with `new` initialiser) | implemented |
| 6 | chained-call return types (`factory.create().work()`) | implemented |
| 7 | superclass members, `super.m()` | implemented |
| 8 | interface members | implemented |
| 9 | overload selection by arity, then by statically known argument types | implemented; undecidable cases return `AMBIGUOUS` |
| 10 | generics | **partial**: generic types are erased for lookup and declared type-variable bounds (`<T extends Base>`) are used. An unbounded `T` resolves to nothing and is reported `UNRESOLVED`; type-argument inference is not implemented. |

### Dependency decisions

| Dependency | Why |
| --- | --- |
| Java 21, Maven | required baseline; virtual threads available for Phase 9 |
| Spring Boot 3.3 | application lifecycle and configuration; the CLI itself stays usable without a Spring context so command tests are fast |
| Picocli | declarative commands, help/version, stable exit codes |
| Tree-sitter (`io.github.bonede`, native bindings) | error-tolerant CST parsing of real-world Java |
| JGit | working-tree and commit diffs without shelling out to `git` |
| Jackson | index and parse-cache serialisation |
| `java.net.http` | GitHub and Groq calls, no extra HTTP stack |
| JUnit 5 | tests |

### Threading model

`IndexingExecutor` supports `SINGLE_THREAD`, `FIXED_POOL` and `VIRTUAL_THREADS`. Guarantees:
results in input order (never completion order), bounded in-flight tasks via a semaphore,
exception isolation per file, cooperative cancellation, and thread-confined Tree-sitter parsers
(`ThreadLocal`, because a `TSParser` owns native state). Only parsing is parallel; symbol
resolution is a single pass over already-parsed facts.

Virtual threads are *not* assumed to be faster: parsing is CPU-bound native work, so
`repo-pilot benchmark --strategies` prints per-strategy timings and asserts that all strategies
produce the same graph digest.

### Persistence strategy

`.repo-pilot/` holds `version`, `metadata.json`, `symbols.json`, `edges.json`, `files.json` and
`parse-cache.json`. Writes are temp-file + atomic move, so an interrupted run cannot leave a
half-written index. Loading validates the format version and the endpoints of every edge; anything
suspicious raises `IndexCorruptException` with a "run `repo-pilot clean && repo-pilot index`"
message instead of returning a partial graph.

### Incremental indexing

Change detection compares size and mtime first, then the SHA-256 of the content, and classifies
files as added / modified / deleted / renamed (renames are detected by identical content hash).
Only changed files are handed to Tree-sitter; unchanged files are served from the parse cache. The
symbol index and graph are then rebuilt from those facts, because a single file change can alter
how references in other files resolve. Digests of an incremental and a full run are identical
(tested).

### Error-handling strategy

* Scanner: every skipped file is recorded with a reason (`IGNORED_DIRECTORY`, `GENERATED`,
  `BINARY`, `SYMLINK`, `TOO_LARGE`, `UNREADABLE`, `LIMIT_EXCEEDED`).
* Parser: malformed sources yield `hasSyntaxErrors` plus diagnostics; surrounding declarations are
  still indexed, and files with no complete declaration contribute nothing rather than something invented.
* Resolver: `RESOLVED` / `AMBIGUOUS` / `UNRESOLVED` / `DYNAMIC`, each with a reason string.
* Network: GitHub 401/404/rate-limit/timeout and Groq timeout/HTTP/malformed-JSON errors become
  actionable messages; AI failure never blocks deterministic analysis.
* CLI: no stack traces, only messages plus the exit codes listed above.

### Testing strategy

140 tests across the phases: CLI behaviour and exit codes, scanner filtering (including symlink
escapes and binary/oversized files), Tree-sitter extraction (nested types, records, enums,
generics, annotations, non-ASCII, malformed input), symbol identity (overloads, arrays, varargs,
same names in different packages), one test per resolution level, graph determinism, persistence
round trips and corruption, incremental reparse counts, concurrency determinism/ordering/bounds/
cancellation, diff parsing (added/deleted/renamed/binary), changed-line→symbol mapping, impact
radius/classification/explanation, Spring DI ambiguity and qualifiers, AI validation (hallucination
rejection, malformed JSON, threshold behaviour), prompt-injection neutralisation and path safety,
plus an end-to-end CLI workflow.

```bash
mvn test          # unit and integration tests
mvn package       # runnable jar
bin/repo-pilot benchmark --dir benchmarks --strategies
```

Bundled benchmark cases include the original five structural regressions plus an issue-to-file case
derived from Apache Kafka PR 1402. The benchmark now reports impact precision/recall/F1 and, where
ground truth exists, issue-to-file Recall@K. These remain regression cases, not a claim of universal
accuracy; a larger real-issue corpus is still required.

### AI usage rules

The LLM is used for exactly one thing: ranking candidate symbols for a natural-language change
description. Consequences:

* Candidates are retrieved deterministically from the index, so the model can only choose among
  symbols that exist.
* Every model answer is validated (JSON shape, symbol membership, numeric confidence in `[0,1]`);
  hallucinated ids are dropped with a note.
* Repository text and user descriptions are sanitised and fenced as untrusted data. Prompt
  injection such as `// AI SYSTEM: ignore previous instructions` is neutralised, and it can never
  introduce a symbol because of the membership check.
* If the best confidence is below `--threshold` (default 0.80), the command prints the alternatives
  and exits with code 3 rather than picking one.
* Without `GROQ_API_KEY` everything still works; ranking is lexical and labelled `deterministic`.
* PR analysis never asks the model what changed — the diff is the source of truth.

## Known limitations

These are real and deliberately not hidden:

1. **Only indexed sources exist in the graph.** JDK and third-party targets are not yet classpath
   indexed. Version 1.1 classifies these separately from project-local gaps so external noise does
   not look like a missing project file. Project-local/dynamic gaps trigger an incompleteness warning.
2. **Generics are partial** (see the level table): no type-argument inference, no wildcard handling.
3. **No dataflow analysis.** Casts, ternaries, parenthesised receivers, array-element receivers and
   collection-element types are not resolved.
4. **Method references have unknown arity**; the functional interface decides it, so a reference to
   an overloaded method is `AMBIGUOUS`.
5. **Reflection and dynamic proxies are unresolvable by construction** and are reported as such.
6. **Lombok and other annotation processors** are not run, so generated members (`@Getter`,
   `@Builder`, `@Data`) do not exist in the index; calls to them are `UNRESOLVED`.
7. **Spring analysis is heuristic**: only annotation-based beans are considered; XML configuration,
   `@ComponentScan` filters, conditional beans, profiles and `BeanFactory` lookups are not modelled.
8. **Field reads/writes are not call sites.** A field access does not create an edge from the
   reading method; only declared types, calls, instantiation and injection do.
9. **Duplicate fully qualified types** (the same FQN in two files) collapse to one symbol,
   deterministically the last one indexed.
10. **Incremental indexing reparses incrementally but re-resolves globally**, which is a correctness
    choice, not an oversight.
11. **Impact radius defaults to 3** and the traversal reports when it stopped; unlimited traversal on
    a large repository is intentionally not the default.
12. **The real-issue benchmark is currently a compact regression fixture derived from Kafka PR
    1402**, not a statistically representative corpus. Use external cases before making accuracy claims.
