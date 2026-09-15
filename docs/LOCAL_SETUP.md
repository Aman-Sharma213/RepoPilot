# Local setup: Windows, macOS, and Linux

## Requirements

- 64-bit JDK 21 (Temurin, Corretto, or Oracle)
- Maven 3.9 or newer
- Git 2.40 or newer
- Internet access for the first Maven build

## Windows 10/11

1. Install JDK 21 and Maven. `winget install EclipseAdoptium.Temurin.21.JDK` and `winget install Apache.Maven` are convenient choices.
2. Restart PowerShell.
3. Verify `java -version`, `javac -version`, `mvn -version`, and `git --version`.
4. If Java is not found, set `JAVA_HOME` to the JDK folder and add `%JAVA_HOME%\bin` to `Path`.
5. Extract the ZIP, open PowerShell in `repo-pilot`, and run:
```powershell
mvn clean test
mvn clean package
.\bin\repo-pilot.ps1 --help
```

## macOS

With Homebrew:
```bash
brew install openjdk@21 maven git
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
java -version && javac -version && mvn -version && git --version
mvn clean test && mvn clean package
chmod +x bin/repo-pilot
bin/repo-pilot --help
```
Add the `JAVA_HOME` export to `~/.zshrc` if required.

## Ubuntu/Debian

```bash
sudo apt update
sudo apt install openjdk-21-jdk maven git
java -version && javac -version && mvn -version && git --version
mvn clean test && mvn clean package
chmod +x bin/repo-pilot
bin/repo-pilot --help
```

## Fedora/RHEL

```bash
sudo dnf install java-21-openjdk-devel maven git
mvn clean test && mvn clean package
```

## Important

- Run Repo Pilot against source you trust. It reads source but does not compile the analyzed repository.
- `.repo-pilot/` is created inside the analyzed repository and should stay ignored by Git.
- Quote symbols containing `#`, parentheses, or spaces.
- For JDK 24+, keep `--enable-native-access=ALL-UNNAMED`; the launchers already add it.
