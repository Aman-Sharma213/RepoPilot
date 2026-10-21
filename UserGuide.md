# Repo Pilot 1.1 — User Guide

Repo Pilot helps developers understand Java repositories and plan code changes.

It supports two main workflows:

1. **Pull-request analysis:** inspect actual changes and their dependency impact.
2. **Description-based planning:** describe an issue or intended change and receive a contribution plan.

Groq is optional and is used to rank candidate symbols for description-based planning.

---

## 1. Requirements

Install:

- JDK 21 or newer.
- Maven.
- Git.
- PowerShell.
- A local copy of the Java repository you want to analyse.

Internet access is required for:

- Downloading Maven dependencies when building Repo Pilot.
- Fetching GitHub PR information and commits.
- Calling Groq in AI-assisted mode.

A Groq API key is required only for Groq-assisted planning.

Check your tools:

```powershell
java -version
mvn -version
git --version
```

Check that Maven is using the intended Java installation.

---

## 2. Open the correct directory

Open PowerShell in the Repo Pilot project root.

This directory contains:

```text
pom.xml
ShellScriptToRunWholeApplication.ps1
bin/
src/
```

Example:

```powershell
Set-Location "C:\Users\abc\Downloads\repo-pilot-v1.1-complete-guide\repo-pilot-v1.1-contributor-guidance"
```

Change this path if your project is stored elsewhere.

---

## 3. Build Repo Pilot

Run:

```powershell
mvn clean package
```

Expected result:

```text
BUILD SUCCESS
```

The application JAR should be created at:

```text
target/repo-pilot.jar
```

Verify it:

```powershell
Test-Path ".\target\repo-pilot.jar"
```

Expected result:

```text
True
```

> Use `mvn clean package`, not `mvn package clean`.
> The second command builds the JAR and then deletes the `target` directory.

Rebuild after changing Java source code.

A rebuild is not required when changing only the PowerShell launch scripts.

---

## 4. Check the application launcher

Run:

```powershell
.\bin\repo-pilot.ps1 --version
```

Expected result:

```text
repo-pilot 1.1.0
```

Display the available commands:

```powershell
.\bin\repo-pilot.ps1 --help
```

### Terminal formatting

The updated PowerShell launchers configure UTF-8 for PowerShell and Java.

Line-drawing output should appear like this:

```text
Repo Pilot
────────────────────────────────────

DIRECTLY MODIFIED
├── ExampleService.firstMethod()
└── ExampleService.secondMethod()
```

If you still see text such as `ΓöÇ`, confirm that you saved the updated launcher and are running through it.

If characters appear as empty boxes, try Windows Terminal with Cascadia Mono or Consolas.

---

## 5. Select a target repository

Set the repository path in your current PowerShell session:

```powershell
$TargetRepo = "C:\Users\abc\Downloads\repo-pilot-targets\kafka"
```

Replace the path with your own local Java repository if needed.

Verify the directory:

```powershell
Test-Path $TargetRepo
```

For a Git repository, inspect its remote:

```powershell
git -C $TargetRepo remote -v
```

> `$TargetRepo` is a PowerShell variable. Set it again after opening a new PowerShell window.

---

## 6. Initialize and index the repository

Prepare the Repo Pilot workspace:

```powershell
.\bin\repo-pilot.ps1 init -r $TargetRepo
```

Build the index:

```powershell
.\bin\repo-pilot.ps1 index -r $TargetRepo
```

Inspect the index status:

```powershell
.\bin\repo-pilot.ps1 status -r $TargetRepo
```

The index provides the symbols and relationships used for planning and impact analysis.

### When to run indexing again

Refresh the index after relevant source changes:

```powershell
.\bin\repo-pilot.ps1 index -r $TargetRepo
```

Do not assume that an existing `.repo-pilot` directory means the index is complete or current.

> Indexing a large repository may take time. Prepare the index before a live demo.

---

## 7. Analyse an existing pull request

The supplied demo script is configured for:

```text
Repository: apache/kafka
Pull request: #1402
Title: MINOR: Catch Throwable in commitSourceTask()
```

### 7.1 Check the script configuration

Open:

```text
ShellScriptToRunWholeApplication.ps1
```

Confirm this path points to your Kafka clone:

```powershell
$TargetRepo = "C:\Users\abc\Downloads\repo-pilot-targets\kafka"
```

The target repository's `origin` remote must provide the expected Kafka PR references.

### 7.2 Run the demo

```powershell
.\ShellScriptToRunWholeApplication.ps1
```

The script:

1. Checks Java, Git, and the Repo Pilot JAR.
2. Verifies the target Git repository.
3. Displays the application version.
4. Checks for the existing Repo Pilot workspace.
5. Retrieves PR metadata and fetches the required commits.
6. Lists changed files and runs impact analysis.

### 7.3 Understand the output

Look for:

```text
ACTUAL PR CHANGES

Deterministic changed targets:

DIRECTLY MODIFIED

INDIRECTLY AFFECTED

TEST IMPACT

ANALYSIS COVERAGE
```

This workflow starts from actual PR changes.

It does not use an issue description to rank targets with Groq.

### Important behavior

- The script reuses the existing index.
- Git fetch operations update local Git objects and references.
- The script does not run a Kafka Maven build or tests.
- Fetching the PR does not check out a different Kafka working tree.

---

## 8. Configure Groq

Groq support is already included in the project.

No additional Java implementation is required to enable it.

### 8.1 Enter your API key

Run in the same PowerShell session where you will launch Repo Pilot:

```powershell
$Key = Read-Host "Enter your Groq API key" -AsSecureString
$env:GROQ_API_KEY = [System.Net.NetworkCredential]::new("", $Key).Password
Remove-Variable Key
```

This avoids displaying the key or placing its actual value in command history.

The key is made available to the Java process through its environment.

Do not put the key in source files, screenshots, or Git commits.

### 8.2 Verify configuration without displaying the key

```powershell
if ([string]::IsNullOrWhiteSpace($env:GROQ_API_KEY)) {
    Write-Host "Groq key is NOT configured"
} else {
    Write-Host "Groq key is configured"
}
```

### 8.3 Model configuration

The project's configured default is:

```text
llama-3.3-70b-versatile
```

Model availability depends on Groq and your account.

To select a different compatible model, add:

```text
--groq-model MODEL_NAME
```

Alternatively, set:

```powershell
$env:GROQ_MODEL = "YOUR_SUPPORTED_MODEL_NAME"
```

Replace the placeholder with a model supported by your Groq account.

---

## 9. Generate a plan from an issue description

Use `plan` to analyse a change that has not yet been written.

Example:

```powershell
.\bin\repo-pilot.ps1 plan `
    "Handle exceptions in WorkerSourceTask.commitSourceTask so task failures do not interrupt offset commits" `
    -r $TargetRepo `
    --mode ai `
    --radius 2 `
    --explain
```

`plan` is an alias of the `diff` command. In this project, that command accepts a natural-language change description.

### Expected output structure

When targets are accepted, the output includes:

```text
CONTRIBUTION PLAN
Requirement: ...
Mode: AI_ASSISTED
Target ranking: groq

START HERE
  <recommended symbol>
  <source file>:<line>
  Why: ...

LIKELY MODIFY
...

READ NEXT / INSPECT FOR IMPACT
...

RELEVANT TESTS
...

Repo Pilot
────────────────────────────────────

Changed:
...

DIRECTLY MODIFIED
...

INDIRECTLY AFFECTED
...

ANALYSIS COVERAGE
...
```

Some sections appear only when matching results exist.

Actual symbols, files, and counts depend on the indexed repository and the ranking result.

### Confirm that Groq was used

Check:

```text
Target ranking: groq
```

If the output says:

```text
Target ranking: deterministic
```

read the accompanying notes to understand why local ranking was used.

> `Mode: AI_ASSISTED` alone does not prove a successful API call.
> The implementation can fall back to deterministic ranking after an API failure.

---

## 10. Choose an analysis mode

### Local mode

```powershell
.\bin\repo-pilot.ps1 plan `
    "WorkerSourceTask commitSourceTask exception handling" `
    -r $TargetRepo `
    --mode local
```

Local mode does not call Groq.

It uses deterministic matching against indexed symbols.

### AI-assisted mode

```powershell
.\bin\repo-pilot.ps1 plan `
    "WorkerSourceTask commitSourceTask exception handling" `
    -r $TargetRepo `
    --mode ai
```

This mode requires a configured key.

API failures can still trigger local fallback.

### Automatic mode

```powershell
.\bin\repo-pilot.ps1 plan `
    "WorkerSourceTask commitSourceTask exception handling" `
    -r $TargetRepo `
    --mode auto
```

Automatic mode uses Groq when configured and otherwise uses local ranking.

### Disable AI explicitly

```powershell
.\bin\repo-pilot.ps1 plan `
    "WorkerSourceTask commitSourceTask exception handling" `
    -r $TargetRepo `
    --no-ai
```

---

## 11. Write useful issue descriptions

Include:

- A relevant class or method name, if known.
- The current problem.
- The intended behavior.

Less useful:

```text
Fix the problem.
```

More useful:

```text
Handle exceptions in WorkerSourceTask.commitSourceTask so failures do not interrupt offset commits.
```

The current retrieval stage uses identifier and type-related text matching.

Groq ranks up to 25 retrieved candidates; it does not independently search the entire repository.

A relevant symbol that is missing from the candidate list cannot be selected by Groq.

---

## 12. Handle ambiguous targets

The default target acceptance threshold is:

```text
0.80
```

If no candidate meets it, the command reports:

```text
Ambiguous target.
```

It may list alternatives.

First:

1. Confirm that you selected the correct repository.
2. Refresh the index if necessary.
3. Make the description more specific.

For exploration, you may lower the threshold:

```powershell
.\bin\repo-pilot.ps1 plan `
    "WorkerSourceTask commitSourceTask exception handling" `
    -r $TargetRepo `
    --mode ai `
    --threshold 0.60 `
    --radius 2
```

> A lower threshold admits weaker matches. It does not make them more correct.

---

## 13. Inspect a known symbol directly

Use `impact` when you already know the symbol:

```powershell
.\bin\repo-pilot.ps1 impact `
    "org.apache.kafka.connect.runtime.WorkerSourceTask#commitSourceTask()" `
    -r $TargetRepo `
    --radius 2 `
    --explain
```

The symbol must exist in the current index.

If the name matches multiple symbols, the command reports alternatives.

This workflow does not require Groq.

---

## 14. Inspect unresolved relationships

To show unresolved evidence:

```powershell
.\bin\repo-pilot.ps1 impact `
    "org.apache.kafka.connect.runtime.WorkerSourceTask#commitSourceTask()" `
    -r $TargetRepo `
    --radius 2 `
    --show-unresolved `
    --unresolved-limit 20
```

Unresolved or dynamic relationships represent analysis gaps.

A high resolved-path strength does not mean that every possible runtime dependency was discovered.

---

## 15. Understand the feature boundaries

### Repo Pilot can provide

- Suggested starting symbols.
- Source-file locations.
- Likely modification targets.
- Dependency-impact paths.
- Related tests when discovered.
- Coverage warnings and unresolved evidence.

### Repo Pilot does not currently provide

- An automatically generated and applied complete bug fix.
- Execution of the target application's tests.
- Proof that a proposed change is correct.
- Guaranteed expected runtime output for an issue.
- Guaranteed discovery of all runtime dependencies.

In description-based planning, labels such as `Changed` and `DIRECTLY MODIFIED` refer to hypothetical selected change targets.

They do not mean that Repo Pilot edited those files.

---

## 16. Remove the API key after use

```powershell
Remove-Item Env:GROQ_API_KEY -ErrorAction SilentlyContinue
```

If you set a model override and want to remove it:

```powershell
Remove-Item Env:GROQ_MODEL -ErrorAction SilentlyContinue
```

---

## 17. Troubleshooting

| Problem | Action |
|---|---|
| Strange text such as `ΓöÇ` | Use the updated UTF-8 PowerShell launchers. |
| Empty boxes instead of lines | Use Windows Terminal and a compatible font. |
| JAR not found | Run `mvn clean package` from the project root. |
| Java not found | Check the JDK installation and PATH. |
| Maven uses the wrong Java version | Check `mvn -version`, JAVA_HOME, and PATH. |
| Script command is not recognized | Use `.\ScriptName.ps1` without a space after `.\`. |
| PowerShell blocks script execution | Follow your machine's approved execution-policy process; do not change organization policy without authorization. |
| Target repository not found | Correct the repository path. |
| No index found or index cannot load | Run `init` and `index`, then inspect `status`. |
| Groq key is missing | Configure it in the same PowerShell session. |
| AI mode falls back locally | Read the notes; check connectivity, key access, model availability, and rate limits. |
| Ambiguous target | Make the description more specific before lowering the threshold. |
| GitHub metadata request fails | Check connectivity and GitHub API limits; configure `GITHUB_TOKEN` if needed. |
| PR fetch fails | Verify that the target repository's `origin` provides the expected PR references. |
| Results do not match the source you expect | Verify the repository version and refresh its index. |

---

## 18. Recommended live-demo sequence

Complete the build and indexing before presenting.

Then:

1. Show the application version.
2. Run the Kafka PR demo.
3. Explain changed targets, indirect impact, and test impact.
4. Configure the Groq key without displaying it.
5. Run `plan` with a specific issue description.
6. Confirm `Target ranking: groq`.
7. Show local mode as the API-free alternative.
8. Explain ambiguity, coverage warnings, and feature limitations.
9. Remove the API key from the session.