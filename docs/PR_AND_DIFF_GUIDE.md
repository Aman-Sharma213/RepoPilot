# Analyze pull requests and diffs

## Before any PR analysis

Build Repo Pilot, clone the target Java repository, and create an index:
```bash
bin/repo-pilot init -r /path/to/target-repo
bin/repo-pilot index -r /path/to/target-repo
```

## GitHub PR

Public repository:
```bash
bin/repo-pilot pr --org apache --repo kafka --pr 1402 -r /path/to/kafka --explain
```
For private repositories or higher GitHub rate limits, set `GITHUB_TOKEN` in the terminal. The local clone must contain the PR base/head commits; fetch them first if necessary.

## Local working tree

```bash
bin/repo-pilot pr --local -r /path/to/target-repo --explain
```
The working tree is mutable, so Repo Pilot may retain the diff as unverified evidence rather than claiming exact symbol mapping.

## Saved diff file — recommended verified workflow

```bash
git -C /path/to/target-repo fetch --all --prune
BASE=$(git -C /path/to/target-repo rev-parse <base-ref>)
HEAD=$(git -C /path/to/target-repo rev-parse <head-ref>)
git -C /path/to/target-repo diff "$BASE" "$HEAD" > change.diff
bin/repo-pilot pr -r /path/to/target-repo --diff-file change.diff --base-sha "$BASE" --head-sha "$HEAD" --explain
```
PowerShell uses `$BASE = git -C ... rev-parse ...`, `$HEAD = ...`, and `git ... diff $BASE $HEAD | Out-File -Encoding utf8 change.diff`.

If `--base-sha` and `--head-sha` are omitted, Repo Pilot intentionally does not pretend the diff maps to exact symbols. It reports file-level evidence and says no verified head symbols are available.

## Natural-language proposed change

```bash
bin/repo-pilot diff "Make token validation reject blank JWTs" -r /path/to/target-repo --threshold 0.80 --explain
```
This is different from a PR: the text retrieves candidate symbols, optionally asks Groq to rank them, validates the response, and then runs impact analysis.
