# Repo Pilot final audit report

Audit date: 2026-09-12

## Verdict

The project structure covers phases 0–20 and the deterministic pipeline is separated into scanner, CST parser, symbol extraction, semantic resolution, graph construction, persistence, diff targeting, impact analysis, AI ranking, and CLI layers. After correcting two outdated end-to-end test expectations, all 140 discovered tests passed.

## Verification performed

- Extracted the supplied ZIP and inventoried 121 production Java files and 22 Java test files.
- Compiled all production sources from scratch for Java 21 against the packaged dependency set.
- Compiled and ran all 140 test methods: **140 passed, 0 failed**.
- Ran 17 CLI scenarios covering help, version, invalid command, not-indexed behavior, init, full index, status, known/unknown impact, deterministic no-key NL fallback, ambiguous NL target, verified and unverified diff handling, incremental index, benchmark, and idempotent clean. All exit codes matched expectations.
- Verified full indexing of a four-file synthetic repository, one-file incremental reparsing, persisted cross-process impact analysis, evidence paths, and verified base/head PR symbol mapping.
- Ran all five bundled benchmark cases across single-thread, fixed-pool, and virtual-thread strategies. Macro precision, recall, and F1 were each 1.000; digests matched across strategies.

## Corrections made

1. Updated a CLI test to expect the intentional 98% confidence cap for field-type receiver resolution instead of 100%.
2. Updated the PR end-to-end test to create real Git base/head commits and provide both SHAs. Unverified diffs are intentionally restricted to file-level evidence.
3. Added beginner setup, Groq, PR/diff, IDE, testing, troubleshooting, and Start Here guides, plus a Windows PowerShell launcher.

## Environment limitation

The audit sandbox had a Java 25 runtime with the Java compiler module but no Maven executable and no network/DNS access to download Maven. Therefore the exact `mvn clean test` and `mvn clean package` commands could not be executed here. Equivalent clean Java 21 compilation and complete test execution were performed using the dependency JARs already packaged in `target/repo-pilot.jar`. Run the Maven commands in `START_HERE.md` on a networked machine with JDK 21 before release.

## Not live-tested

- A real Groq request, because no API key/network was available. Unit tests cover unavailable service, malformed JSON, invalid confidence, and hallucinated symbols.
- A live GitHub API PR request, because network was unavailable. Local verified base/head PR analysis and GitHub failure behavior were tested.
- Accuracy on a large real-world repository. The bundled benchmark corpus is deliberately synthetic.

## Remaining documented limitations

Classpath/JAR symbols are not indexed; generics and Spring behavior are partial/heuristic; generated Lombok members, reflection, proxies, and full dataflow are not resolved. See `README.md` for the complete list.
