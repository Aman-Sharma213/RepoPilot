# Jenkins PR #23905 — RepoPilot validation report

## Scope

RepoPilot 1.0.0 was executed against the supplied `jenkins-master.zip` source snapshot and the supplied unified diff for Jenkins PR #23905. Three independent full indexes were created using `SINGLE_THREAD`, `FIXED_POOL`, and `VIRTUAL_THREADS`.

Important qualification: the supplied Jenkins ZIP contains no `.git` directory or commit SHA. It is therefore not possible to prove that this snapshot is the exact PR base revision. The observed incorrect changed-line mapping is consistent with source-line drift between the ZIP and the PR base.

## Actual PR files changed

1. `core/src/main/java/jenkins/model/Jenkins.java`
2. `core/src/main/resources/hudson/model/View/index.jelly`
3. `core/src/main/resources/hudson/model/View/new-view-page.jelly`
4. `core/src/main/resources/jenkins/model/Jenkins/configure.jelly`
5. `core/src/main/resources/lib/layout/layout.jelly`
6. `core/src/main/resources/lib/layout/main-panel.jelly`
7. `src/main/scss/components/_index.scss`
8. `src/main/scss/components/_system-message.scss` (new)
9. `src/main/scss/pages/_dashboard.scss`
10. `war/src/main/resources/images/symbols/construct-outline.svg` (new)
11. `war/src/main/resources/images/symbols/warning-outline.svg`

The Java changes add `systemMessageSeverity`, `getSystemMessageSeverity()`, `setSystemMessageSeverity(String)`, and persistence in `doConfigSubmit`. The other ten files move/render the banner, configure radio buttons, add severity styling, and add/update icons.

## Index results

All modes indexed the same 1,920 Java files and produced exactly the same semantic graph:

| Mode | Threads | RepoPilot duration | Measured wall time | Symbols | Edges | Digest |
|---|---:|---:|---:|---:|---:|---|
| SINGLE_THREAD | 1 | 94,693 ms | 100,162 ms | 43,449 | 94,254 | `7ae5265b48b2ab8b` |
| FIXED_POOL | 2 | 81,373 ms | 86,949 ms | 43,449 | 94,254 | `7ae5265b48b2ab8b` |
| VIRTUAL_THREADS | 2 | 80,363 ms | 85,016 ms | 43,449 | 94,254 | `7ae5265b48b2ab8b` |

Other identical statistics:

- 1,920 files parsed; none reused from cache.
- 181,119 unresolved references.
- 34% relationship-resolution rate.
- One Java file had syntax errors and was indexed partially.

Relative wall-time results:

- FIXED_POOL was 13.2% faster than SINGLE_THREAD.
- VIRTUAL_THREADS was 15.1% faster than SINGLE_THREAD.
- VIRTUAL_THREADS was 2.2% faster than FIXED_POOL; this small difference is not enough to establish a general performance advantage from one run.

The identical digest and counts confirm deterministic concurrency behavior for this repository.

## RepoPilot PR result

RepoPilot reported:

- 11 changed files.
- Only 2 changed indexed symbols: `jenkins.model.Jenkins` and `Jenkins#getPlugins(Class)`.
- 2,207 impacted symbols across 690 files at radius 3.
- Overall displayed confidence: 96%.
- 65,842 unresolved call sites excluded from the impact set.
- All ten non-Java files were explicitly skipped as “not a Java source file.”

The mode-specific graph was identical, so the deterministic PR result is also identical across the three execution strategies.

## Correctness assessment

**Overall verdict: the result is not correct enough for this real PR.** Concurrency and graph determinism work, but the changed-symbol identification and practical impact set are poor for this mixed Java/Jelly/SCSS change.

### Correct observations

- RepoPilot correctly parsed all 11 diff file sections.
- It correctly recognized that only one changed file is Java.
- It correctly reported unsupported non-Java files rather than inventing symbols.
- The graph and results were deterministic across all execution modes.

### Missed impacts / false negatives

RepoPilot failed to identify the actual Java members changed by the PR:

- `Jenkins#systemMessageSeverity`
- `Jenkins#getSystemMessageSeverity()`
- `Jenkins#setSystemMessageSeverity(String)`
- `Jenkins#doConfigSubmit(StaplerRequest2, StaplerResponse2)`

It also cannot model the most important runtime dependencies in this PR:

- Jelly expressions reading `app.systemMessageSeverity` and `app.systemMessage`.
- Stapler/config-form binding for `systemMessageSeverity`.
- Markup formatter invocation from Jelly.
- SCSS import and CSS-class relationships.
- SVG icon references such as `symbol-construct-outline`.
- Movement of the system-message rendering from view-specific Jelly to the shared main panel.

These are not minor omissions: ten of eleven changed files are outside RepoPilot’s Java-only graph and implement most of the user-visible feature.

### False positives

`Jenkins#getPlugins(Class)` was reported as directly modified even though PR #23905 does not change that method. This is a changed-line-to-symbol mapping error, most likely caused by applying PR hunk line numbers to a Jenkins snapshot that is not the exact base revision.

Mapping a small field/addition hunk to the entire `Jenkins` class then caused reverse traversal through broad type, constructor, return-type, and call relationships. The resulting 2,207 symbols / 690 files include large portions of Jenkins that have no plausible behavioral dependency on system-message severity. Examples include plugin management, CLI commands, security configuration, queue operations, agents, tool installation, and unrelated tests.

Thus, the 96% displayed confidence is misleading: it measures confidence along graph edges after incorrect targets were selected; it does not measure confidence that the diff was mapped to the right changed symbols.

## Product issues found and recommendations

1. **Require/index the exact PR base revision.** Reject or prominently warn when the index revision cannot be matched to the diff base SHA.
2. **Handle added declarations.** A base-only graph cannot contain newly added fields/methods. Parse the patched/head version or construct changed symbols from the added source.
3. **Use old-line ranges for deletions and new-line ranges against a head index.** Do not apply new-file hunk coordinates blindly to an unrelated snapshot.
4. **Do not treat an enclosing top-level class as an equivalent replacement for an unmapped member addition.** Mark it as coarse/uncertain and avoid unrestricted fan-out.
5. **Separate structural type impact from executable call impact.** `PARAMETER_TYPE`, `RETURN_TYPE`, and broad class ownership should not automatically imply the same change impact as a direct call.
6. **Add mixed-language resource analysis.** At minimum, connect Jelly property expressions, Stapler form names, SCSS imports/classes, and Jenkins symbol assets.
7. **Report target-mapping confidence separately from edge-path confidence.** A high path confidence must not hide a low-confidence changed target.
8. **Add output caps/summaries.** A 2,207-symbol result needs ranking, grouping, and a default concise output mode to remain actionable.

## Runtime defect fixed during validation

The supplied JAR reproducibly crashed on the large Jenkins repository under JDK 25 with `SIGILL` in native Tree-sitter child traversal. `TSNode` is a native view owned by `TSTree`; after `getRootNode()`, an optimizing JVM could consider the local tree reference dead while extraction still traversed nodes, allowing native memory to be cleaned too early.

`TreeSitterParser.parse` now keeps the owning `TSTree` reachable through the entire extraction with `java.lang.ref.Reference.reachabilityFence(tree)`. The patched class compiled successfully against the packaged dependencies. After this fix, all three full Jenkins indexes completed successfully and produced identical digests.

## Test conclusion

RepoPilot demonstrates a functioning deterministic Java indexer and concurrency implementation on a large codebase. For this particular PR, however, impact-analysis accuracy is low because the feature is predominantly implemented in Jelly/SCSS/SVG and because changed Java lines were mapped against a source snapshot without a verifiable matching base revision. The tool should not present the 2,207-symbol set as a reliable impact analysis for PR #23905.
