## Real-World Testing and Demonstrations

RepoPilot was tested against the open-source
[Jenkins codebase](https://github.com/jenkinsci/jenkins).

The demonstrations cover the workflow from initializing and indexing
a repository to analyzing a pull request and finding potential starting
points for an issue-based contribution.

### 1. Repository Initialization and Indexing

This demo shows how RepoPilot initializes its workspace and indexes
the Jenkins codebase.

**Commands demonstrated:** `init`, `index`, `plan` for one of the real issue

**Video:** 

https://github.com/user-attachments/assets/89d10791-95f3-4ae7-b5e9-e40f147038f1


### 2. Pull Request Impact Analysis

This demo shows how a reviewer can analyze a Jenkins pull request,
inspect changed files, and explore potential impact.

**Command demonstrated:** `pr`

**Video:** <img width="194" height="102" alt="Adobe Express - Jenkins-PR-RepoPillot-1" src="https://github.com/user-attachments/assets/8639d1e9-c3a6-413e-8064-694ef195a3e5" />

<img width="186" height="160" alt="Adobe Express - Jenkins-PR-ByRepoPilot2" src="https://github.com/user-attachments/assets/8858592d-2085-4c30-a743-eaf99ad70e34" />


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
<img width="400" height="212" alt="JenkinWithGroq2" src="https://github.com/user-attachments/assets/ba3b6dd7-9a69-4c9e-9e69-64bd02cc4f1e" />


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
