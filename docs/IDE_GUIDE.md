# VS Code and IntelliJ IDEA

## VS Code

1. Install Extension Pack for Java.
2. Choose **File → Open Folder** and open the extracted `repo-pilot` folder.
3. Wait for Maven import to finish.
4. Open the integrated terminal and run `mvn clean test` and `mvn clean package`.
5. To analyze the repository currently open in VS Code, run the built JAR with `-r` pointing to that repository.
6. For a Git diff, use Source Control, or run `git diff BASE HEAD > change.diff`; then use the verified command in `PR_AND_DIFF_GUIDE.md`.

Optional `.vscode/launch.json` Java application settings: main class `com.repopilot.RepoPilotApplication`; program arguments such as `index -r /absolute/path/to/project`; VM argument `--enable-native-access=ALL-UNNAMED`.

## IntelliJ IDEA

1. Choose **Open** and select `pom.xml` or the project folder.
2. Set **Project SDK** and Maven runner JRE to JDK 21.
3. Reload Maven, then run Lifecycle `clean`, `test`, and `package`.
4. Create an Application run configuration:
   - Main class: `com.repopilot.RepoPilotApplication`
   - JRE: 21
   - VM options: `--enable-native-access=ALL-UNNAMED`
   - Program arguments: `index -r /absolute/path/to/project`
   - Working directory: the Repo Pilot project folder
5. IntelliJ's Git Log can copy base/head commit hashes. Export a patch with Git tools or run `git diff BASE HEAD > change.diff`, then execute Repo Pilot with both SHAs.

## Debugging

Set breakpoints in `cli`, `resolve`, `graph`, or `impact`; run the Application configuration in Debug. The analyzed repository is data and does not need to be imported as a module.
