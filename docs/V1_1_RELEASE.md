# Repo Pilot 1.1 — Contributor Guidance Release

This version implements the eight highest-priority changes selected for contributor usefulness and
interview-ready engineering depth.

## Implemented

1. **Concise output** — unresolved evidence is hidden by default. Use `--show-unresolved` and
   `--unresolved-limit N` for diagnostics.
2. **Unresolved classification** — JDK, dependency, project-local, and dynamic gaps are reported
   separately.
3. **Coverage warnings** — `COMPLETE_ENOUGH`, `PARTIAL`, or `LOW_COVERAGE` describes graph
   completeness. Project-local/dynamic gaps warn that affected files may be missing.
4. **Repository statistics** — indexed production/test files, source lines/bytes, types, callables,
   nodes, edges, duration, and coverage are visible in `index` and `status`.
5. **Mode switching** — `--mode local`, `--mode ai`, and `--mode auto`; `--no-ai` is an explicit
   privacy switch.
6. **Secure Groq BYOK** — only environment-variable names are accepted; secrets are never printed
   or persisted. AI mode fails clearly without a key and auto mode falls back locally.
7. **Guided contribution plan** — `plan` (alias of `diff`) renders START HERE, LIKELY MODIFY,
   READ NEXT / INSPECT FOR IMPACT, and RELEVANT TESTS.
8. **Issue-to-code benchmark support** — benchmark definitions may contain a natural-language
   description, expected top files, and K; the runner reports Recall@K. A Kafka PR 1402-derived
   regression fixture is bundled.

## Examples

```powershell
# No API key; no external AI request
java -jar target/repo-pilot.jar plan "handle source task commit failures" -r C:\code\kafka --mode local

# Use GROQ_API_KEY when available and fall back locally otherwise
java -jar target/repo-pilot.jar plan "handle source task commit failures" -r C:\code\kafka --mode auto

# Require Groq, reading the key from a custom environment variable
$env:MY_GROQ_KEY = "your-key"
java -jar target/repo-pilot.jar plan "handle source task commit failures" -r C:\code\kafka `
  --mode ai --groq-key-env MY_GROQ_KEY

# Show at most 20 detailed gaps
java -jar target/repo-pilot.jar impact "WorkerSourceTask.commitTaskRecord" -r C:\code\kafka `
  --show-unresolved --unresolved-limit 20
```

## Trust boundary

Git, parsing, symbol identity, relationship resolution, graph traversal, and result validation stay
deterministic and local. Groq only ranks offered symbols and supplies short reasons. It cannot add a
symbol that is not present in the local index. `LIKELY MODIFY` remains a recommendation rather than
a guarantee.
