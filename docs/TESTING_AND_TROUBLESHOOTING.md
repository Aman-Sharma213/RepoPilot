# Testing and troubleshooting

## Required verification

```bash
mvn clean test
mvn clean package
bin/repo-pilot --version
bin/repo-pilot benchmark --dir benchmarks --strategies
```
Windows uses `.\bin\repo-pilot.ps1`.

## Common problems

- `java`/`javac` not found: install a JDK 21 and set `JAVA_HOME`.
- Maven uses another Java: inspect the Java home in `mvn -version`.
- `repo-pilot.jar not found`: run `mvn clean package` in the extracted Repo Pilot folder.
- Native-access warning: use the supplied launcher or add `--enable-native-access=ALL-UNNAMED`.
- Exit 4: run `init` and `index` for the target repository.
- Exit 3: target is ambiguous; use a more specific symbol/description or intentionally adjust `--threshold`.
- Corrupt index: run `clean`, then `index --full`.
- GitHub failure: verify network, `GITHUB_TOKEN`, repository spelling, rate limit, and that the local clone contains base/head commits.
- Groq failure: the deterministic fallback remains available; verify `GROQ_API_KEY` without printing it.

## Exit codes

`0` success, `1` runtime/error result, `2` invalid command or argument, `3` ambiguous target, `4` repository not indexed.
