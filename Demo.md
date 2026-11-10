## Real-World Testing and Demonstrations

RepoPilot was tested against the open-source
[Jenkins codebase](https://github.com/jenkinsci/jenkins).

The demonstrations cover the workflow from initializing and indexing
a repository to analyzing a pull request and finding potential starting
points for an issue-based contribution.

### 1. Repository Initialization and Indexing

This demo shows how RepoPilot initializes its workspace and indexes
the Jenkins codebase.

**Commands demonstrated:** `init`, `index`

**Video:** [JenkinsDemo.mp4](../../JenkinsDemo.mp4)

### 2. Pull Request Impact Analysis

This demo shows how a reviewer can analyze a Jenkins pull request,
inspect changed files, and explore potential impact.

**Command demonstrated:** `pr`

**Video:** [Jenkins-PR-RepoPillot-1.mp4](../../../Videos/Captures/Jenkins-PR-RepoPillot-1.mp4)
[Jenkins-PR-ByRepoPilot2.mp4](../../../Videos/Captures/Jenkins-PR-ByRepoPilot2.mp4)

> In the recorded run, the PR head revision could not be verified.
> The output therefore provides file-level change evidence, rather
> than verified symbol-level impact traversal.

### 3. Finding a Starting Point for Contribution

This demo shows how a contributor can describe an issue and use
RepoPilot to identify candidate code locations to investigate.

**Command demonstrated:** `plan`

**Video:** first demo itself has the plan command demonstration 

### 4. BYOK Groq API usage , just for plan making (codebase never been handed over to the LLM )

**Demo Video** 
[JenkinWithGroq.mp4](../../../Videos/Captures/JenkinWithGroq.mp4)
[JenkinWithGroq2.mp4](../../../Videos/Captures/JenkinWithGroq2.mp4)

> This recording uses optional Groq-assisted ranking. RepoPilot also
> supports local planning without an LLM.

## How RepoPilot Uses AI

RepoPilot was developed with minimal AI assistance.

Its core PR analysis and impact exploration do not require an LLM.
RepoPilot uses Tree-sitter to parse source code and builds a code graph
for analysis. AI-assisted ranking is an optional feature for
contribution planning; local planning is also available.

## Current Limitations

- Static analysis may miss dynamic behavior and unresolved relationships.
- Indexing coverage depends on supported files, scanning rules, and
  parsing outcomes. Check the index status and warnings before treating
  results as representative of the repository.
- Suggested contribution locations are starting points for investigation,
  not guaranteed fixes.