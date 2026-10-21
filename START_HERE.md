# Repo Pilot — Complete Setup and Usage Guide

Repo Pilot analyzes Java repositories and answers: **Where should I start, what may be affected, and why?** This guide covers installation, building, indexing a project, every main command, and troubleshooting.

## 1. Requirements

Install:

- **JDK 21 or newer** (a JDK, not only a JRE)
- **Maven 3.9+** when building from source
- **Git 2.40+** for local-change and revision-aware workflows

Verify:

```text
java -version
javac -version
mvn -version
git --version
```

`java` and `javac` should report 21 or newer. Maven should use the same Java installation.

## 2. Extract Repo Pilot

Extract the ZIP to a permanent folder.

Example locations:

- Windows: `C:\Tools\repo-pilot`
- macOS/Linux: `~/tools/repo-pilot`

Windows PowerShell:

```powershell
cd "C:\Tools\repo-pilot"
```

macOS/Linux:

```bash
cd ~/tools/repo-pilot
```

## 3. Verify or build the executable JAR

The correct executable is the larger Spring Boot JAR:

```text
target/repo-pilot.jar
```

Do **not** run `repo-pilot.jar.original`; it is Maven's smaller, non-executable thin JAR.

Verify the included executable:

```powershell
java --enable-native-access=ALL-UNNAMED -jar target\repo-pilot.jar --version
java --enable-native-access=ALL-UNNAMED -jar target\repo-pilot.jar --help
```

Expected version:

```text
repo-pilot 1.1.0
```

To build from source:

```text
mvn clean test
mvn clean package
```

To package without rerunning tests:

```text
mvn clean package -DskipTests
```

## 4. Select the Java project to analyze

Keep these locations separate:

- **Repo Pilot folder** — contains `target/repo-pilot.jar`
- **Target project** — the Java repository you want to analyze

Windows PowerShell variables:

```powershell
$RepoPilot = "C:\Tools\repo-pilot"
$Project = "C:\Projects\my-java-project"
$Jar = "$RepoPilot\target\repo-pilot.jar"
```

macOS/Linux variables:

```bash
REPO_PILOT="$HOME/tools/repo-pilot"
PROJECT="$HOME/projects/my-java-project"
JAR="$REPO_PILOT/target/repo-pilot.jar"
```

All examples use `-r` (the short form of `--repo-path`), so they can be run from any directory.

## 5. Initialize the project

Windows:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar init -r $Project
```

macOS/Linux:

```bash
java --enable-native-access=ALL-UNNAMED -jar "$JAR" init -r "$PROJECT"
```

`init` creates `.repo-pilot/`, writes the index format version, adds `.repo-pilot/` to `.gitignore` when appropriate, and reports discovered Java files. It is safe to run more than once.

## 6. Index the codebase

First or incremental index:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar index -r $Project
```

Repo Pilot scans Java files, parses them with Tree-sitter, extracts symbols, resolves relationships, builds a code graph, and saves it under the target project's `.repo-pilot/` directory. Running the same command later reuses unchanged files from the parse cache.

Force a complete rebuild:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar index -r $Project --full
```

Fixed thread pool:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar index -r $Project --strategy FIXED_POOL --threads 4
```

Virtual threads:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar index -r $Project --strategy VIRTUAL_THREADS --threads 8
```

Available strategies are `SINGLE_THREAD`, `FIXED_POOL`, and `VIRTUAL_THREADS`. Disable Spring-specific analysis with `--no-spring`.

## 7. Check status

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar status -r $Project
```

The report includes the Git revision, indexed files, source size, symbols, graph nodes and edges, resolution coverage, unresolved relationships, and edge counts. If it says the repository is not indexed, run `index` first.

## 8. Analyze symbol impact

Prefer a fully qualified symbol:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar impact "com.example.auth.AuthService#validate(Token)" -r $Project --explain
```

Other useful options:

```powershell
# Increase traversal depth
java --enable-native-access=ALL-UNNAMED -jar $Jar impact "AuthService.validate" -r $Project --radius 5

# Exclude test sources
java --enable-native-access=ALL-UNNAMED -jar $Jar impact "AuthService.validate" -r $Project --no-tests

# Include unresolved evidence
java --enable-native-access=ALL-UNNAMED -jar $Jar impact "AuthService.validate" -r $Project --show-unresolved --unresolved-limit 50
```

Accepted lookup styles include:

```text
com.example.auth.AuthService
com.example.auth.AuthService#validate(Token)
AuthService.validate
validate
```

If a short name is ambiguous, add the package, owning type, method, and parameter types.

## 9. Generate a contribution plan

`plan` and `diff` are aliases. They map a natural-language change description to indexed symbols and impact evidence.

Fully local mode:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar plan "make token validation stricter" -r $Project --mode local
```

Equivalent command:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar diff "make token validation stricter" -r $Project --no-ai
```

Automatic mode uses Groq when configured and otherwise falls back locally:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar plan "add audit logging to login failures" -r $Project --mode auto
```

For AI mode, set the key only in the environment:

```powershell
$env:GROQ_API_KEY = "your-key"
java --enable-native-access=ALL-UNNAMED -jar $Jar plan "add audit logging to login failures" -r $Project --mode ai
```

Useful controls:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar plan "your change" -r $Project `
  --mode auto `
  --threshold 0.70 `
  --radius 4 `
  --explain `
  --show-unresolved
```

Use `--groq-key-env NAME` for another key variable and `--groq-model MODEL` for a model override. The key is not persisted.

## 10. Analyze diffs and pull requests

### Diff file

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar pr -r $Project --diff-file "C:\Temp\change.diff"
```

Create a diff file from Git:

```powershell
git -C $Project diff > C:\Temp\change.diff
```

Provide verified revisions when available:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar pr -r $Project `
  --diff-file "C:\Temp\change.diff" `
  --base-sha BASE_COMMIT_SHA `
  --head-sha HEAD_COMMIT_SHA `
  --explain
```

### Uncommitted changes

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar pr -r $Project --local --explain
```

### GitHub pull request

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar pr -r $Project --org OWNER --repo REPOSITORY --pr 123 --explain
```

For private repositories or higher API limits:

```powershell
$env:GITHUB_TOKEN = "your-token"
```

The local repository should contain the relevant base and head commits for fully revision-verified mapping.

## 11. Run benchmarks

Run from the Repo Pilot folder because `benchmarks` is stored there:

```powershell
cd $RepoPilot
java --enable-native-access=ALL-UNNAMED -jar $Jar benchmark --dir benchmarks
```

Compare execution strategies:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar benchmark --dir benchmarks --strategies
```

The benchmark reports precision, recall, F1, timing, and issue-to-file Recall@K where ground truth exists.

## 12. Clean the index

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar clean -r $Project
```

This deletes only the target project's `.repo-pilot/` directory. It does not delete source code, and running it repeatedly is safe.

Recreate the index:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar init -r $Project
java --enable-native-access=ALL-UNNAMED -jar $Jar index -r $Project --full
```

## 13. Recommended daily workflow

Initial analysis:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar init -r $Project
java --enable-native-access=ALL-UNNAMED -jar $Jar index -r $Project
java --enable-native-access=ALL-UNNAMED -jar $Jar status -r $Project
java --enable-native-access=ALL-UNNAMED -jar $Jar plan "describe the intended change" -r $Project --mode local
java --enable-native-access=ALL-UNNAMED -jar $Jar impact "package.Type#method(ParameterType)" -r $Project --explain
```

After changing source files:

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar index -r $Project
java --enable-native-access=ALL-UNNAMED -jar $Jar pr -r $Project --local --explain
```

## 14. Exit codes

| Code | Meaning |
| ---: | --- |
| `0` | Success |
| `1` | Runtime or analysis error |
| `2` | Invalid usage or option |
| `3` | Ambiguous target |
| `4` | Repository is not indexed |

PowerShell exposes the last code as `$LASTEXITCODE`.

## 15. Windows troubleshooting

### `A required privilege is not held by the client`

The security suite creates a symbolic link to verify that scans cannot escape the repository. Windows may block this unless Developer Mode is enabled or PowerShell is elevated. The test now skips when symlinks are unavailable.

To run it instead of skipping it, enable **Settings → System → For developers → Developer Mode**, or run PowerShell as Administrator, then rerun `mvn clean test`.

### Tree-sitter native-access warning

Launch with:

```text
--enable-native-access=ALL-UNNAMED
```

Maven tests already receive this option from the Surefire configuration.

### `no main manifest attribute` with `repo-pilot.jar.original`

Use the larger executable:

```text
target/repo-pilot.jar
```

Do not run `target/repo-pilot.jar.original`.

### Project is not indexed

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar index -r $Project
```

### Ambiguous target

Use `package.Type#method(ParameterType)` or make the plan description more specific. Lower `--threshold` only when appropriate.

### Corrupt or incompatible index

```powershell
java --enable-native-access=ALL-UNNAMED -jar $Jar clean -r $Project
java --enable-native-access=ALL-UNNAMED -jar $Jar index -r $Project --full
```

## 16. More documentation

- `README.md` — architecture and features
- `docs/LOCAL_SETUP.md` — detailed local setup
- `docs/GROQ_SETUP.md` — Groq configuration
- `docs/PR_AND_DIFF_GUIDE.md` — diff and PR workflows
- `docs/IDE_GUIDE.md` — IDE usage
- `docs/TESTING_AND_TROUBLESHOOTING.md` — testing and diagnostics
- `docs/AUDIT_REPORT.md` — audit details
