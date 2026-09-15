# Repo Pilot — Start Here

This guide assumes you are new to Java command-line projects.

## 1. Copy and extract

1. Copy `repo-pilot-final-audited.zip` to a folder you can find.
2. Extract it. The extracted folder is `repo-pilot`.
3. Open a terminal in that folder.

Windows PowerShell:
```powershell
cd "$HOME\Downloads\repo-pilot"
```
macOS/Linux:
```bash
cd ~/Downloads/repo-pilot
```

## 2. Install requirements

Install **JDK 21** (not only a JRE), **Maven 3.9+**, and **Git 2.40+**. Check:
```text
java -version
javac -version
mvn -version
git --version
```
`java` and `javac` should report 21. Maven should show the same Java home.

## 3. Build and test

```bash
mvn clean test
mvn clean package
```
The runnable file will be `target/repo-pilot.jar`.

## 4. First run

```bash
java --enable-native-access=ALL-UNNAMED -jar target/repo-pilot.jar --version
java --enable-native-access=ALL-UNNAMED -jar target/repo-pilot.jar --help
```
On macOS/Linux, `chmod +x bin/repo-pilot` lets you use `bin/repo-pilot`. On Windows, use the Java command or `bin\repo-pilot.ps1`.

## 5. Analyze another Java repository

From the Repo Pilot folder:
```bash
bin/repo-pilot init -r /path/to/java-project
bin/repo-pilot index -r /path/to/java-project
bin/repo-pilot status -r /path/to/java-project
bin/repo-pilot impact "com.example.Service#method(Type)" -r /path/to/java-project --explain
```

Read next: `docs/LOCAL_SETUP.md`, `docs/GROQ_SETUP.md`, `docs/PR_AND_DIFF_GUIDE.md`, and `docs/IDE_GUIDE.md`.
