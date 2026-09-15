# RepoPilot – Contributor Saathi
## Complete Teacher-Led Interview Masterclass

**Project version taught:** 1.1.0  
**Goal:** help you explain the project naturally, defend every technical claim, walk through the actual code, and handle interviewer follow-ups without memorizing disconnected definitions.

> **Teacher rule:** Do not begin an interview by listing technologies. Begin with the contributor's problem, show the journey of one PR, and introduce each technology only when the story needs it.

---

# How to Use This Guide

Study in five rounds:

1. **Story round:** learn the 30-second and 2-minute project answers.
2. **Flow round:** draw the architecture and data flow from memory.
3. **Concept round:** learn what each technology/concept means.
4. **Code round:** open the mentioned source file and explain the important code.
5. **Attack round:** answer the follow-up questions without exaggerating.

For every topic, use this speaking pattern:

```text
Context / problem
    ↓
Concept
    ↓
Why this project needs it
    ↓
How RepoPilot implements it
    ↓
Code location
    ↓
Limitation / trade-off
```

This pattern prevents a boring “definition dump.”

---

# Lesson 1 — First Understand the Product Story

## 1.1 Create the context

Imagine a contributor opens Apache Kafka. There are hundreds of Java files. The contributor receives a PR or issue involving one method but does not know:

- where the change starts;
- which method contains the changed lines;
- which callers or implementations depend on it;
- which tests are statically connected;
- how far the effect may travel.

Manual text search can find names, but it does not automatically explain dependency direction or provide a verified path from a Git diff to a Java declaration.

RepoPilot addresses that initial investigation problem.

## 1.2 Exact 30-second answer

> “RepoPilot is a Java command-line static-analysis tool for contributors working with unfamiliar Java repositories. It maps revision-verified pull-request diff hunks to Java symbols, builds a repository-local dependency graph, and uses reverse breadth-first traversal to show the changed symbols, dependent code, and statically connected tests within a configurable radius. It gives an evidence-backed inspection set, not a guarantee that every returned file must be modified.”

## 1.3 Exact 2-minute answer

> “A contributor first indexes a local Java repository. RepoPilot safely scans Java files, parses them using Tree-sitter, extracts declarations such as classes, methods, constructors, fields, and parameters, and creates stable symbol IDs. Because Tree-sitter only gives syntax, I added a custom semantic-resolution layer using imports, packages, scopes, fields, parameters, locals, inheritance, interfaces, method arity, and basic argument-type hints. Resolved relationships become directed graph edges.
>
> For a PR, GitHub provides the base SHA, head SHA, title, state, and unified diff. JGit verifies the commits in the local clone and materializes both immutable source trees without changing the working branch. RepoPilot validates each hunk, maps removed lines to base symbols and added lines to head symbols, and uses exact head symbols as starting nodes. Reverse BFS follows incoming dependency edges and groups radius-zero targets, indirect dependents, uncertain relationships, and tests.
>
> There is also a natural-language `plan` command. It locally retrieves existing symbols and can optionally ask Groq to rerank only those candidates. AI does not build the graph or analyze the PR. The important limitation is that this is repository-local approximate semantic analysis, not a complete Java compiler, so unresolved external, generated, reflective, or dynamic relationships can hide results.”

## 1.4 What NOT to say

Do not say:

- “It finds every file that must change.”
- “Tree-sitter gives a semantic AST.”
- “Groq analyzes the complete PR.”
- “JGit clones and checks out the PR.”
- “143 tests ran in 21 milliseconds.”
- “Jenkins was fully benchmarked end-to-end.”

Say instead:

- “It returns a radius-bounded, evidence-backed inspection set.”
- “Tree-sitter gives a CST; RepoPilot builds a smaller domain model and approximate semantic graph.”
- “Groq only reranks existing candidates in `plan` mode.”
- “The user supplies a local clone; JGit verifies and reads commit trees.”
- “The v1.1 audit recorded 143 passing tests in about 3.7 seconds.”

## Likely interview questions

### Q: What is the single biggest value?

> “Reducing the contributor's initial search space while preserving explainability. Instead of only returning filenames, RepoPilot can show the changed symbol and the edge path through which another symbol depends on it.”

### Q: Is this a code-review tool?

> “No. It does not judge correctness, style, or security of a contribution. It is a source-navigation and static change-impact tool.”

### Q: Does it modify code?

> “No. It produces guidance and evidence. Automatic patch generation is not implemented.”

---

# Lesson 2 — Architecture as a Story

## 2.1 Draw this architecture

```text
Executable JAR
RepoPilotApplication (Spring Boot non-web launcher)
        ↓
RootCommand + subcommands (Picocli)
        ↓
Input path
├── index/status/impact
├── GitHub PR / diff / local Git
└── natural-language plan
        ↓
RepositoryScanner + IncrementalIndexer
        ↓
TreeSitterParser
        ↓
ParseResult domain model
        ↓
SymbolExtractor + SymbolIndex
        ↓
SymbolResolver + optional SpringDependencyResolver
        ↓
GraphBuilder + CodeGraph
        ↓
ChangedTargetLocator or CandidateRetriever
        ↓
ImpactEngine reverse BFS
        ↓
Ui / ContributionPlanRenderer
```

## 2.2 How to explain the architecture

> “I divided the system according to trust and responsibility. The CLI layer only collects input. The scan and parse layers produce syntax facts. The symbol and resolver layers add repository-local meaning. The graph layer stores only indexed symbols and explicit relationships. Git decides what actually changed, while the impact engine calculates reverse dependency paths. Optional AI is outside the correctness core and can only rank symbols already created by the deterministic pipeline.”

## Follow-up: Why layered architecture?

> “It separates syntax from semantics and facts from ranking. That made it possible to test each phase independently, keep the graph deterministic when Groq is unavailable, and report unresolved relationships instead of hiding them.”

## Follow-up: Is this clean architecture?

> “It has layered and pipeline characteristics, but I would not claim strict Clean Architecture. Command classes directly construct several services, and external adapters are concrete classes rather than dependency-inverted interfaces.”

## Exact folders to open

```text
src/main/java/com/repopilot/
├── cli/          command parsing and output
├── scan/         safe repository discovery
├── parse/        Tree-sitter and syntax records
├── symbol/       symbol identity and lookup
├── resolve/      custom semantic resolution
├── graph/        directed dependency graph
├── impact/       reverse BFS and classification
├── git/          diffs, revisions and hunk mapping
├── github/       PR REST client
├── ai/           local retrieval, Groq and validation
├── concurrent/   parsing executor strategies
├── incremental/  fingerprints and parse cache
├── persist/      JSON index
├── spring/       target-repository Spring heuristics
└── benchmark/    regression metrics
```

---

# Lesson 3 — Picocli: From Definition to Actual Code

## 3.1 Context first

RepoPilot supports commands such as `index`, `impact`, `pr`, and `plan`. Writing manual `String[] args` parsing would create repetitive validation, weak help output, and fragile subcommand dispatch. That is where Picocli enters the story.

## 3.2 What is Picocli?

Picocli is a Java library that maps command-line arguments to annotated classes and fields. It supports subcommands, positional parameters, named options, help/version generation, type conversion, mixins, exit codes, and testable command execution.

## 3.3 Exact interview answer

> “I used Picocli as the CLI adapter. Each operation is a `Callable<Integer>` command. Picocli converts raw process arguments into typed fields, chooses the correct subcommand, generates usage text, and gives the command configured output streams. This keeps argument parsing separate from the indexing and graph logic.”

## 3.4 Exact implementation

Open:

```text
src/main/java/com/repopilot/cli/RootCommand.java
```

Important code:

```java
@Command(
    name = "repo-pilot",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    subcommands = {
        InitCommand.class,
        IndexCommand.class,
        StatusCommand.class,
        ImpactCommand.class,
        PrCommand.class,
        DiffCommand.class,
        BenchmarkCommand.class,
        CleanCommand.class
    })
public class RootCommand implements Callable<Integer> { ... }
```

Explain it:

> “`@Command` declares the root command and registers the supported subcommands. `mixinStandardHelpOptions` gives `-h` and `-V`. Each subcommand returns an integer exit code, which is useful for scripts and tests.”

Open:

```text
src/main/java/com/repopilot/cli/ImpactCommand.java
```

```java
@Parameters(index = "0")
String symbol;

@Mixin
RepoOption repo = new RepoOption();

@Option(names = "--radius")
int radius = 3;
```

Explain it:

> “The symbol is positional, repository path is reused through a mixin, and radius is a typed option with a default. In `call()`, the command loads the index, resolves the requested symbol, constructs `ImpactEngine.Options`, invokes analysis, and renders the result.”

Open:

```text
src/main/java/com/repopilot/cli/RepoOption.java
```

> “This class centralizes `-r/--repo-path`. It defaults to the current directory, normalizes the path, and rejects a non-directory before analysis starts.”

## 3.5 Alternatives

| Alternative | Why not chosen / trade-off |
|---|---|
| Manual argument parsing | too much repetitive parsing/help/error code |
| Apache Commons CLI | suitable for options but less natural for nested annotated subcommands |
| JCommander | annotation based, but Picocli has stronger subcommand/help/type-conversion support |
| Spring Shell | interactive shell orientation and heavier runtime than needed |
| Clikt | Kotlin-first; project is Java |

## 3.6 Advantages in this project

- command classes can be unit tested without starting Spring;
- enum conversion for `ExecutionStrategy`;
- reusable mixins;
- generated help/version;
- stable integer exit codes;
- typed `Path`, `Integer`, `double`, and boolean options.

## 3.7 Limitations

Picocli validates syntax and basic type conversion, but domain rules remain in code. For example, `PrCommand` checks that `--org`, `--repo`, and `--pr` are supplied together, and `ImpactEngine.Options` rejects negative radius.

## Picocli interview drill

### Q: How does CLI input reach the engine?

> “Picocli populates annotated command fields, invokes `call()`, and the command translates those fields into domain objects. For `impact`, the string goes through `SymbolLookup`; the result becomes a `SymbolId` list passed to `ImpactEngine`.”

### Q: Why does every command implement `Callable<Integer>`?

> “The return value becomes a stable process exit status: 0 success, 1 runtime error, 2 usage error, 3 ambiguous target, and 4 missing index.”

### Q: Is Picocli part of static analysis?

> “No. It is only the delivery adapter. The core index and impact classes can be called directly in tests.”

---

# Lesson 4 — Spring Boot: Be Honest About Its Small Role

## Context

The project needs an executable lifecycle and Spring-compatible Picocli factory, but it is not a REST backend.

Open:

```text
src/main/java/com/repopilot/RepoPilotApplication.java
```

```java
@SpringBootApplication
public class RepoPilotApplication
        implements CommandLineRunner, ExitCodeGenerator {

    private final IFactory factory;

    public void run(String... args) {
        this.exitCode = new CommandLine(new RootCommand(), factory).execute(args);
    }
}
```

## What to say

> “Spring Boot is a thin non-web launcher. `RepoPilotApplication` starts with `WebApplicationType.NONE`, receives Picocli's factory, executes the root command as a `CommandLineRunner`, and returns its exit code. The analysis services are mostly manually composed and do not require the Spring container.”

## Why choose it?

> “It gave me a standard executable-JAR lifecycle and Picocli integration. However, I would acknowledge that plain Picocli with a normal `main` method could reduce startup and dependency weight because there are no controllers or web endpoints.”

## Alternatives

- plain Java `main` + Picocli: simpler and lighter;
- Quarkus/Micronaut: useful for native/cloud apps, unnecessary here;
- Spring Shell: better for interactive sessions, not required.

## Follow-up traps

### Q: Which classes are Spring beans?

> “The only project class explicitly annotated for Spring is `RepoPilotApplication`. There are no service/controller/repository annotations in the main code. Picocli's Spring starter supplies the factory integration.”

### Q: Where are your REST APIs?

> “There are none. RepoPilot is a CLI and runs with `WebApplicationType.NONE`.”

### Q: Then why call yourself a Java backend developer?

> “The project demonstrates backend engineering concepts—parsing pipelines, graph algorithms, persistence, concurrency, HTTP clients, failure handling, and testing—but it is not itself an HTTP backend service.”


---

# Lesson 5 — Repository Scanning and Security Before Parsing

## Context

A repository is untrusted input. It may contain build output, generated source, huge files, binaries, symlinks, or paths that escape the root. Sending everything directly to a native parser would waste time and increase risk.

## Concept

A repository scanner walks a directory tree and creates a controlled, deterministic input set. Validation at the boundary is important because later phases should not repeatedly ask whether a path is safe.

## Actual code

Open:

```text
src/main/java/com/repopilot/scan/RepositoryScanner.java
src/main/java/com/repopilot/scan/FileFilter.java
src/main/java/com/repopilot/security/SafePath.java
src/main/java/com/repopilot/security/ResourceLimits.java
```

The scanner uses `Files.walkFileTree` without following symbolic links. It skips `.git`, `.repo-pilot`, `target`, `build`, IDE directories, dependency caches, and generated-source paths. It accepts Java files except `module-info.java` and `package-info.java`.

Default limits:

```text
2 MiB per Java file
64 directory levels
200,000 visited files
4 GiB accepted Java source
```

Important path logic:

```java
Path resolved = root.resolve(rel).normalize();
if (!isInside(root, resolved)) {
    throw new SecurityException("Path escapes repository root");
}
```

## What to say

> “I treat repository content as untrusted. Before parsing, `RepositoryScanner` creates a sorted list of accepted Java files. It never follows symlinks, rejects binary/generated/build files, validates paths stay under the repository root, and enforces file-count, depth, per-file, and aggregate-byte limits. Every skipped file keeps a reason so missing input is explainable.”

## Why Java NIO?

Java NIO provides portable path normalization, attributes, directory traversal, symbolic-link checks, and streaming file operations without external shell commands.

## Alternatives

- Apache Commons IO: convenient utilities but unnecessary for the core walk.
- invoking `find`: platform-dependent and increases command-injection surface.
- trusting Git-tracked file lists: would require Git and would exclude non-Git repositories.

## Questions

### Q: Does path normalization alone stop symlink escape?

> “No. Normalization stops `..` traversal but not a symlink whose real target is outside root. The scanner therefore does not follow links, explicitly rejects symbolic links, and snapshot extraction also skips Git symlink entries.”

### Q: Is malicious Java executed?

> “No. RepoPilot reads and parses text. It does not compile or execute target-project code. Native parser vulnerabilities and resource exhaustion are still risks, so limits and dependency maintenance remain important.”

---

# Lesson 6 — Tree-sitter, CST, and the Parsing Layer

## 6.1 Context

After scanning, RepoPilot needs declarations and exact ranges from source—even if a repository does not compile or contains an incomplete file. Regex is too fragile for nested Java syntax. A full compiler provides semantics but normally needs correct classpaths and build configuration.

## 6.2 What is Tree-sitter?

Tree-sitter is an incremental parser framework with language grammars. For Java, it converts source text into a grammar-shaped concrete syntax tree with node types and byte/row/column ranges.

## 6.3 CST versus AST

Say this precisely:

> “Tree-sitter gives RepoPilot a CST, not compiler semantic information. RepoPilot then extracts selected CST facts into records such as `TypeDeclaration` and `MethodDeclaration`. Those records are AST-like domain objects, but they are not a complete Java AST and do not contain resolved types.”

Why the distinction matters:

```text
CST can tell us: validator.validate(token) is a method_invocation.
CST cannot tell us: validator has type AuthValidator and this selects validate(Token).
```

The resolver must add that second part.

## 6.4 Actual code

Open:

```text
src/main/java/com/repopilot/parse/TreeSitterParser.java
```

Initialization:

```java
private static final ThreadLocal<TSParser> PARSERS =
    ThreadLocal.withInitial(() -> {
        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterJava());
        return parser;
    });
```

What it does:

- `TSParser` owns native state and is not treated as thread-safe.
- Each parsing thread gets a separate parser.
- `TreeSitterJava` installs the Java grammar.

Parsing:

```java
tree = PARSERS.get().parseString(null, source);
TSNode root = tree.getRootNode();
```

The `null` means no previous tree is supplied. Tree-sitter's incremental edit API is therefore **not used**.

Top-level extraction matches node types:

```java
case "package_declaration"
case "import_declaration"
case "class_declaration"
case "interface_declaration"
case "enum_declaration"
case "record_declaration"
case "annotation_type_declaration"
```

Method-body traversal recognizes:

```text
method_invocation
explicit_constructor_invocation
method_reference
object_creation_expression
local_variable_declaration
```

Text extraction:

```java
return new String(source, start, end - start, StandardCharsets.UTF_8);
```

Location extraction preserves file, 1-based line/column, and 0-based UTF-8 byte ranges.

## 6.5 What gets extracted

- package and imports;
- classes, interfaces, enums, records, annotation types;
- nested types;
- methods, regular/compact constructors;
- fields and record-component fields;
- parameters and varargs;
- modifiers and annotations;
- method calls and method references;
- object creation;
- local variables for scope resolution;
- source locations and syntax-error status.

## 6.6 Error behavior

> “If Tree-sitter reports errors, RepoPilot marks `hasSyntaxErrors` but can retain valid declarations from the recovered tree. An unreadable, oversized, null, or parser-failure input produces `ParseResult.failed` instead of terminating the entire repository index.”

## 6.7 Why Tree-sitter over alternatives?

| Tool | Strength | Reason not selected for this implementation |
|---|---|---|
| `javac` Tree API | compiler-correct attribution | needs build/classpath/module setup; incomplete projects may fail |
| Eclipse JDT | powerful Java semantic model | heavier integration and environment setup |
| JavaParser + Symbol Solver | Java-friendly AST/API | symbol solving still needs dependency/classpath configuration |
| Spoon | rich source transformation | heavier and more transformation-oriented |
| Regex | simple | cannot reliably parse nested/overloaded/annotated Java |

Best interview answer:

> “Tree-sitter gave tolerant parsing and exact ranges independent of a successful Maven build. The trade-off is that I had to implement approximate semantic resolution, so I explicitly report unresolved relationships.”

## Follow-up questions

### Q: Why not call it an AST?

> “Because that would suggest compiler-level semantic nodes. The direct input is a CST. My extracted records discard punctuation and statements, so they are a selected syntax model, not the full CST or a type-attributed AST.”

### Q: Does Tree-sitter parse files incrementally?

> “RepoPilot reuses whole-file `ParseResult`s by content hash. A changed file is fully reparsed; it does not call Tree-sitter's edit-based incremental tree API.”

### Q: How are overloaded methods represented at parse time?

> “The parser extracts parameter types as written. `SymbolExtractor` later includes erased parameter types in the symbol ID, so overloads remain distinct.”

---

# Lesson 7 — Symbol Extraction and Identity

## Context

Graph nodes need stable identities. A method name alone is insufficient because different classes and overloads can share it.

## Concept

A symbol is a declared program element with identity and metadata. RepoPilot supports type, method, constructor, field, and parameter symbols.

## Actual files

```text
src/main/java/com/repopilot/symbol/Symbol.java
src/main/java/com/repopilot/symbol/SymbolId.java
src/main/java/com/repopilot/symbol/SymbolKind.java
src/main/java/com/repopilot/symbol/SymbolExtractor.java
src/main/java/com/repopilot/symbol/SymbolIndex.java
```

Identity examples:

```text
Type:        com.example.AuthService
Nested type: com.example.AuthService.Inner
Method:      com.example.AuthService#validate(Token)
Constructor: com.example.AuthService#<init>(Token)
Field:       com.example.AuthService#secret
Parameter:   com.example.AuthService#validate(Token)::token
```

## What `SymbolExtractor` does

```java
List<String> paramTypes = method.parameters().stream()
    .map(ParameterDeclaration::erasedType)
    .toList();

SymbolId methodId = method.constructor()
    ? SymbolId.ofConstructor(fqn, paramTypes)
    : SymbolId.ofMethod(fqn, method.name(), paramTypes);
```

Explain it:

> “For every parsed declaration, I construct a stable textual ID. Method and constructor IDs include owner and parameter signature, so overloads do not collide. Generic arguments are removed for identity, arrays are preserved, and varargs are treated as arrays.”

`Symbol` stores:

```text
id, kind, simple name, package, owner,
parameter types, return/declared type,
raw supertypes, modifiers, annotations,
type parameters, source location
```

`SymbolIndex` uses sorted maps for lookup by ID, type FQN, simple type name, owner, and file.

## Important limitations

- Types are erased “as written,” not compiler-canonical fully qualified signatures.
- local variables are scope facts, not graph symbols;
- enum constants are parsed but not symbol nodes;
- duplicate identical FQNs replace the prior symbol;
- generated members do not exist unless present in source.

## Questions

### Q: Why not use an integer ID?

> “Textual IDs are deterministic across runs and easy to persist, inspect, and validate in AI responses. An internal numeric ID could reduce memory, but it would require a stable mapping layer.”

### Q: How do `foo(int)` and `foo(String)` differ?

> “Their `SymbolId` parameter lists differ. Method resolution later selects among them using arity and basic argument-type hints.”

### Q: Why include parameter symbols?

> “They preserve declaration identity and metadata. Dependency edges for parameter types originate from the method, because impact is more useful at callable level.”

---

# Lesson 8 — Semantic Resolution: The Core Technical Challenge

## 8.1 Context

Tree-sitter sees text. RepoPilot must decide whether `service.process(order)` refers to a particular indexed method. That requires name and type resolution.

## 8.2 Architecture

```text
SymbolResolver
├── ImportResolver
├── TypeResolver
├── InheritanceResolver
└── MethodResolver
```

Open:

```text
src/main/java/com/repopilot/resolve/
```

## 8.3 Type resolution order

`ImportResolver` tries:

1. indexed fully qualified name;
2. nested/enclosing declared types;
3. explicit import;
4. same package;
5. wildcard import with exactly one match;
6. unique repository-wide simple name at lower confidence;
7. ambiguous or unresolved.

What to say:

> “I only resolve to symbols that exist in the local index. I never create a fake JDK or dependency node. If two simple names remain possible, the result is `AMBIGUOUS`; if none exists, it is `UNRESOLVED`.”

## 8.4 Receiver resolution

`TypeResolver` distinguishes:

- unqualified/`this` calls;
- `super`;
- parameter, local, or field receiver;
- static receiver written as a type;
- field chains;
- `new Type()`;
- limited chained-call return types;
- bounded generic variables.

Example:

```java
validator.validate(token);
```

Flow:

```text
receiver text "validator"
    ↓
method scope variables
    ↓ parameter/local/field declaration
written type "AuthValidator"
    ↓ imports/package lookup
FQN com.example.AuthValidator
    ↓ MethodResolver
validate method candidates
```

## 8.5 Method resolution

`MethodResolver`:

1. searches receiver and supertypes for the method name;
2. removes same-signature duplicates inherited through overrides;
3. filters by argument count, including basic varargs;
4. compares known argument hints;
5. supports basic numeric widening, null/nonprimitive, and indexed subtype checks;
6. returns one resolved target or several ambiguous targets.

Argument hints come from literals, `new X`, and visible identifier types. Unknown expressions remain unknown.

## 8.6 Resolution result model

```text
RESOLVED   exactly one target
AMBIGUOUS  several candidates
UNRESOLVED no indexed target
DYNAMIC    runtime-decided relationship
```

Every `Resolution` carries candidates, confidence, resolution level, and human-readable reason.

## 8.7 Exact interview wording

> “RepoPilot performs approximate repository-local semantic resolution. It supports a practical resolution ladder—same class, imports, package, field/parameter/local types, chained return types, inheritance, interfaces, overloads, and declared generic bounds. It is not compiler-complete because there is no classpath attribution, full generic inference, accessibility analysis, autoboxing specificity, or data flow.”

## Alternatives

- `javac`/JDT: stronger correctness; harder setup and dependency resolution.
- JavaParser Symbol Solver: closer Java model; still requires type solvers/classpath.
- language-server protocol: could reuse IDE index but adds process/protocol dependency.

## Attack questions

### Q: Can it resolve `list.get(0).validate()`?

> “Usually not reliably. `List` is external and generic element-type inference is absent. The unresolved relationship is retained and can create a missing-impact warning.”

### Q: What about polymorphism?

> “Declared interface/superclass calls and override edges are represented, but the runtime object is not proven. Override propagation receives a confidence penalty and may be classified uncertain.”

### Q: Does it support generics?

> “Partially. It erases generic arguments for identity and can follow a declared upper bound such as `T extends Base`. It does not perform type-argument inference, wildcard capture, or substitution.”

### Q: Is confidence a probability?

> “No. It is a deterministic heuristic strength used for classification and path ranking, not a statistically calibrated probability.”

---

# Lesson 9 — Building the Dependency Graph

## Context

After resolution, RepoPilot needs a structure that can answer both “what does A depend on?” and “who depends on B?” efficiently.

## Actual files

```text
src/main/java/com/repopilot/graph/CodeGraph.java
src/main/java/com/repopilot/graph/GraphBuilder.java
src/main/java/com/repopilot/graph/GraphEdge.java
src/main/java/com/repopilot/graph/EdgeType.java
```

## Graph meaning

```text
A --CALLS--> B
```

means **A depends on B**. If B changes, A is a possible impact candidate.

A node is always an actual indexed symbol. An edge stores:

```text
source, target, type, source location,
confidence, resolution status, resolution level, evidence
```

Main edge types:

```text
CALLS, EXTENDS, IMPLEMENTS, OVERRIDES,
FIELD_TYPE, PARAMETER_TYPE, RETURN_TYPE,
INSTANTIATES, IMPORTS, ANNOTATED_WITH,
SPRING_DI, EVENT_LISTENER, BEAN_CREATION
```

`IMPORTS` and `ANNOTATED_WITH` are recorded but do not propagate impact.

## Data structures

```java
Map<SymbolId, GraphNode> nodes;
Map<SymbolId, List<GraphEdge>> outgoing;
Map<SymbolId, List<GraphEdge>> incoming;
List<GraphEdge> edges;
List<UnresolvedReference> unresolved;
```

## What to say

> “I store both outgoing and incoming adjacency. Outgoing answers what a symbol depends on. Incoming is effectively the reverse index needed for change impact. Resolved references create edges; ambiguous references create candidate edges marked ambiguous; unresolved references remain separate evidence because there is no valid target node.”

## Follow-ups

### Q: Is the graph directed?

> “Yes. Direction preserves dependency semantics. Caller to callee is not interchangeable with callee to caller.”

### Q: Are duplicate edges removed?

> “No. `addEdge` validates endpoints but does not deduplicate. Deterministic sorting orders them. Final impacted symbols are collapsed by the BFS best-path map, but edge-level deduplication is a possible improvement.”

### Q: Why not Neo4j?

> “The current graph is repository-local, loaded in one process, and primarily needs adjacency traversal. In-memory maps keep deployment simple. A persistent graph or embedded database may help at much larger scale.”

---

# Lesson 10 — Reverse BFS and Impact Radius

## Context

The stored edge points from dependent to dependency. A normal outgoing traversal from a changed method would show what it calls, not who might be affected by changing it. RepoPilot must traverse in reverse.

Open:

```text
src/main/java/com/repopilot/impact/ImpactEngine.java
```

## Algorithm

```text
changed/selected symbols = radius 0
    ↓ queue all targets
read incoming propagating edges
    ↓
edge source becomes dependent = radius + 1
    ↓
store confidence and full evidence path
    ↓
repeat until radius limit
```

RepoPilot uses:

```java
Deque<Frame> queue = new ArrayDeque<>();
Map<SymbolId, AffectedSymbol> best = new HashMap<>();
```

It is multi-source BFS because multiple changed/accepted symbols can enter the queue at radius zero.

## Why `best` instead of a plain visited set?

A symbol may be reached through multiple paths. `better()` prefers:

1. shorter radius;
2. certain over uncertain;
3. higher confidence;
4. deterministic path-key order.

Stale queued frames are skipped.

## Confidence

Path confidence is multiplied across edges. A forward override traversal applies an additional 0.8 factor because dispatch through a contract is weaker evidence.

## Exact answer

> “I chose BFS because radius means shortest dependency distance. A DFS could find a long path before a short one and would require extra shortest-path logic. Incoming adjacency makes reverse traversal efficient, while the best-path map keeps cycles safe and preserves the strongest shortest evidence path.”

## Dry run

```text
Controller → Service
Job        → Service
Test       → Controller
```

If `Service` changes:

```text
radius 0: Service
radius 1: Controller, Job
radius 2: Test
```

If `Test` is recognized as test code, it is labeled `TEST_IMPACT`.

## Complexity

> “For the reachable subgraph within the chosen radius, core traversal is O(V + E) time and O(V + E) state. Sorting and evidence-path copying add overhead.”

## Traps

### Q: Does radius 1 mean directly modified?

> “No. Radius zero is the selected/changed symbol. Radius one is a direct dependent and is printed as indirectly affected.”

### Q: Can cycles cause an infinite loop?

> “No. A symbol is only requeued when the candidate path is better than the stored result. A cycle normally increases radius and therefore cannot replace a shorter result.”

### Q: Why is `OVERRIDES` special?

> “An implementation points to the overridden contract. To reach callers typed against the contract, impact may need to move from the implementation toward that contract, so outgoing override edges are also considered with reduced confidence.”


---

# Lesson 11 — GitHub API, Local Git, and JGit: Never Mix Their Roles

## 11.1 Context

A remote PR page, a local Git object database, and a Java Git library are different things. Strong interviewers will test whether you understand that separation.

## 11.2 One-line distinction

```text
GitHub API → tells RepoPilot which PR and diff to analyze.
Local .git → contains commit, tree, and blob objects.
JGit → Java code used to read those local objects and generate local diffs.
```

## 11.3 GitHub API actual use

Open:

```text
src/main/java/com/repopilot/github/GitHubClient.java
```

RepoPilot calls:

```text
GET https://api.github.com/repos/{org}/{repo}/pulls/{number}
```

Twice with different `Accept` headers:

```text
application/vnd.github+json
application/vnd.github.v3.diff
```

JSON fields used:

```text
title, state, base.sha, head.sha
```

The unified-diff response is passed to `UnifiedDiffParser`.

## Exact speaking answer

> “GitHub is only the remote PR-fact provider. I use the pull-request endpoint once as JSON for title, state, base SHA, and head SHA, and once with the diff media type for unified diff text. The PR title is shown to the user but is not analyzed. PR body, comments, labels, and linked issues are not implemented.”

## Authentication and failures

- optional `GITHUB_TOKEN` environment variable;
- Bearer header;
- explicit messages for 401, 404, rate-limit exhaustion, timeout, malformed JSON, and network errors;
- no retries, backoff, pagination loop, or rate-reset scheduling.

## 11.4 JGit actual use

Open:

```text
src/main/java/com/repopilot/git/GitService.java
src/main/java/com/repopilot/git/GitRevisionSnapshotService.java
```

`GitService` performs:

- local `.git` open through `FileRepositoryBuilder`;
- HEAD resolution and branch lookup;
- HEAD-to-working-tree diff;
- base-to-head tree diff;
- rename detection in `DiffFormatter`.

`GitRevisionSnapshotService` performs:

- `Repository.resolve(revision + "^{commit}")`;
- `RevWalk.parseCommit`;
- recursive `TreeWalk`;
- blob copy into a temporary directory;
- tree/commit provenance recording;
- full indexing of the materialized snapshot.

## What JGit does NOT do

- clone repository;
- fetch PR commits;
- checkout/switch branch;
- merge;
- push;
- calculate a merge base;
- modify the user's working tree.

## Why JGit?

> “I wanted local Git access inside Java without invoking platform-specific shell commands. JGit gives typed object, tree, and diff APIs and lets me materialize an immutable commit without checking it out. The trade-off is extra library complexity, and my current code still expects the user to fetch required commits.”

## Alternatives

| Alternative | Advantage | Trade-off |
|---|---|---|
| Git CLI via `ProcessBuilder` | exact parity with installed Git, easy fetch commands | platform dependency, command/security/error parsing |
| libgit2/JNI | high-performance native Git | native packaging complexity |
| GitHub files endpoint only | no local Git required for changed filenames | cannot independently map exact old/new source unless content is also fetched |

## Questions

### Q: If GitHub already gives the diff, why JGit?

> “The diff gives coordinates, but exact mapping requires the source content from the matching base and head revisions. JGit verifies those commits locally and provides immutable tree snapshots. Without that, line numbers could be interpreted against the wrong checkout.”

### Q: What breaks if JGit is removed?

> “HEAD/branch status, local working-tree diff, revision-to-revision diff, commit verification, snapshot materialization, and verified PR symbol mapping stop working. Indexing, direct symbol impact, natural-language plan, and unverified diff-file parsing could still work.”

### Q: Does remote PR mode automatically work after a normal clone?

> “Only if the local object database contains both base and head SHAs. RepoPilot does not fetch missing PR commits.”

---

# Lesson 12 — Unified Diff Parsing and Revision-Verified Symbol Mapping

## Context

Changed filenames are not enough. To seed method-level impact, RepoPilot must determine which declaration contains each changed line, and it must do so against the correct revision.

## 12.1 Unified diff model

Open:

```text
src/main/java/com/repopilot/git/UnifiedDiffParser.java
src/main/java/com/repopilot/git/FileDiff.java
src/main/java/com/repopilot/git/DiffHunk.java
```

The parser retains:

```text
old/new path
change type
binary flag
old/new hunk coordinates
context lines
added lines
removed lines
contiguous added/removed ranges
```

`DiffHunk.matches` walks source lines and verifies that context/removal/addition text matches the correct base/head source.

## 12.2 Mapping algorithm

Open:

```text
src/main/java/com/repopilot/git/RevisionAwareChangedTargetLocator.java
```

Flow:

```text
old removed range → search only base SymbolIndex
new added range   → search only head SymbolIndex
```

For each range, RepoPilot selects non-parameter symbols whose location intersects it. Members are preferred. A type is selected only when its declaration start line is touched; the code intentionally avoids claiming the whole class for an arbitrary body line.

Classification examples:

```text
same ID in base and head → METHOD_MODIFIED / FIELD_MODIFIED
base-only ID             → METHOD_DELETED / FIELD_DELETED
head-only ID             → METHOD_ADDED / FIELD_ADDED
```

## Exact speaking answer

> “The mapper is revision aware. I do not take changed line 50 and look it up only in the current checkout. Removed ranges belong to the base index, additions belong to the head index, and each hunk must match both source snapshots. Only then can the result be labeled `EXACT`.”

## Special cases

- file addition/deletion → file target plus every declaration on existing side;
- rename without content → file rename target;
- import-only → import classification;
- simple comment-only/whitespace-only → file-level classification;
- annotation-only → annotation change classification;
- binary/non-Java → file-level evidence;
- unsupported non-Java → no semantic Java edge;
- one-line multiple members → ambiguous candidates;
- stale diff → source-content mismatch;
- missing revision → unverified file-level result.

## Non-Java observations

`NonJavaChangeAnalyzer` uses regexes to find structural strings in Jelly/XML/CSS/SCSS/SVG/YAML/JSON/properties. Every result is marked `semanticallyVerified=false` and is never inserted into the Java graph.

## Questions

### Q: How do you handle deleted methods?

> “The removed range maps to the base symbol and the target is classified as deleted. Because that symbol does not exist in the head graph, current head impact traversal cannot start from it. Historical/deletion impact is a known partial area.”

### Q: What if a method signature changes?

> “Its stable ID changes, so the old declaration appears deleted and the new declaration appears added rather than one modified symbol.”

### Q: Why avoid class fallback?

> “A hunk outside a member—such as whitespace or import changes—does not prove that the entire class declaration changed semantically. Earlier broad fallback can create misleading direct targets, so the revision-aware path prefers honest file-level output.”

### Q: Is your diff parser complete for every Git diff?

> “No. It supports normal unified Git diffs, paths, additions, deletions, renames, binary markers, and hunks. Combined merge diffs, all quoting cases, submodule details, and every Git edge form are not proven.”

---

# Lesson 13 — The Three PR Modes and Their Different Guarantees

## Remote GitHub PR

```text
--org --repo --pr
```

- fetches metadata/diff;
- verifies/indexes local base and head if available;
- exact symbols and BFS only when both verify.

## Diff file

```text
--diff-file change.diff --base-sha B --head-sha H
```

- exact only if both SHAs supplied and locally resolvable;
- otherwise file-level unverified evidence.

## Local working tree

```text
--local
```

- JGit generates HEAD-versus-working-tree diff;
- HEAD snapshot verifies;
- working tree is intentionally marked unverified;
- current code does not build a working-tree head index;
- no exact head symbols/BFS.

## Interview answer

> “The guarantee depends on provenance. Remote or diff-file analysis is exact only when both revisions can be materialized and the hunk matches their contents. Local working-tree mode currently generates correct file/hunk evidence but stops at file level because the mutable head is not indexed as a verified snapshot.”

## Improvement answer

> “To complete local mode, I would create a controlled working-tree snapshot, index it, record a content manifest/hash rather than a commit SHA, validate hunks against HEAD and that snapshot, and clearly distinguish content-verified from commit-verified provenance.”

---

# Lesson 14 — Test Impact: What It Is and What It Is Not

## Context

A contributor wants to know which tests are worth checking. RepoPilot does not use test coverage; it treats tests as symbols in the same dependency graph.

Open:

```text
src/main/java/com/repopilot/symbol/Symbol.java
src/main/java/com/repopilot/impact/ImpactClassifier.java
src/main/java/com/repopilot/cli/ContributionPlanRenderer.java
```

Test detection:

```java
return file.contains("src/test/")
    || file.contains("/test/java/")
    || simpleName.endsWith("Test")
    || simpleName.endsWith("Tests")
    || simpleName.endsWith("IT");
```

If a test method calls production code, the graph has:

```text
TestMethod → ProductionMethod
```

A reverse traversal from the production method reaches the test and labels it `TEST_IMPACT`.

## What to say

> “My test impact is static dependency-based. Test Java is indexed like production Java. If a test symbol is reachable through graph relationships from the changed target, it is displayed separately. It does not execute tests or use coverage, so reflective and framework-driven tests may be missed.”

## Alternatives

- JaCoCo/runtime coverage: more precise executed dependencies but needs representative test runs.
- Maven Surefire selection: knows test discovery but not necessarily semantic dependency.
- mutation testing: validates test effectiveness but expensive.

## Questions

### Q: Are integration tests supported?

> “Only heuristically. A class/path ending in `IT` or under test paths can be marked test code, and it must have a static graph path. There is no framework-specific integration-test engine.”

### Q: Does RepoPilot run the suggested tests?

> “No. It identifies test symbols for inspection; execution is not implemented.”

---

# Lesson 15 — Static Spring Analysis vs Spring Boot Runtime

These are two separate uses of the word Spring.

```text
RepoPilot runtime Spring Boot → launches the CLI.
SpringDependencyResolver → analyzes annotations inside the target repository.
```

Open:

```text
src/main/java/com/repopilot/spring/SpringDependencyResolver.java
```

Implemented heuristics:

- stereotypes: `Service`, `Component`, `Repository`, `Controller`, `RestController`, `Configuration`, and advice variants;
- field injection via `Autowired`, `Inject`, or `Resource`;
- constructor injection for annotated constructors or a single constructor;
- interface implementation candidate selection;
- `@Qualifier` default-name matching;
- one `@Primary` bean;
- `@Bean` return type;
- `@EventListener` first parameter.

## Exact answer

> “Plain Java resolution cannot see runtime container wiring. I added a separate resolver that emits `SPRING_DI`, `BEAN_CREATION`, and `EVENT_LISTENER` edges. Keeping these as distinct edge types lets the user audit or disable them with `--no-spring`.”

## Limitations

- no XML configuration;
- no profiles/conditions/scopes;
- no complete component scanning;
- no factory/proxy semantics;
- no collection/map injection;
- no configuration properties;
- no actual Spring container boot for the target project;
- qualifier matching is simplified.

## Question: Why separate Spring edges?

> “They are framework inference, not ordinary Java reference binding. Separate types keep evidence honest and make uncertainty visible.”

---

# Lesson 16 — Concurrency and Virtual Threads

## Context

Parsing hundreds of independent files is naturally parallelizable. Symbol resolution is different because it needs the complete symbol index.

Open:

```text
src/main/java/com/repopilot/concurrent/ExecutionStrategy.java
src/main/java/com/repopilot/concurrent/IndexingExecutor.java
src/main/java/com/repopilot/index/ParseCoordinator.java
```

Strategies:

```text
SINGLE_THREAD
FIXED_POOL
VIRTUAL_THREADS
```

Fixed pool:

```java
Executors.newFixedThreadPool(parallelism, namedFactory())
```

Virtual threads:

```java
Executors.newVirtualThreadPerTaskExecutor()
```

A `Semaphore(parallelism)` caps active tasks even with virtual threads.

## Determinism

Tasks are submitted in sorted file order. Futures are collected in the same input order, not completion order. Later symbol and graph phases sort their data.

## Thread safety

- each thread gets its own native `TSParser` through `ThreadLocal`;
- parse output is per-file immutable/copy-protected records;
- shared `SymbolIndex` and `CodeGraph` are mutated only sequentially after parsing;
- completion count uses `AtomicInteger`;
- cancellation flag is `volatile`.

## What to say

> “Only per-file parsing is concurrent. I intentionally keep global symbol resolution and graph mutation sequential to avoid races and nondeterministic merges. Fixed and virtual-thread strategies produce the same graph digest in tests.”

## Why virtual threads may not help

> “Virtual threads are strongest for blocking I/O. Tree-sitter parsing is CPU-heavy native work and may pin carrier threads, so I expose the strategy but do not claim it is automatically faster.”

## Alternatives

- parallel streams: concise but weaker control over errors, cancellation, naming, and boundedness;
- `CompletableFuture`: flexible pipeline composition but unnecessary here;
- ForkJoinPool: useful for recursive CPU work; file tasks are simple and bounded;
- process pool: stronger isolation but higher serialization/startup cost.

## Questions

### Q: What happens when one parser task fails?

> “Its exception is converted into `ParseResult.failed`; other futures continue and output order remains stable.”

### Q: How many threads by default?

> “The CLI defaults to `SINGLE_THREAD` and one thread. The user must explicitly select a different strategy and count.”

### Q: Any concurrency bug remaining?

> “The wrapper creates thread-local parsers, but explicit `TreeSitterParser.close()` is not consistently called by `ParseCoordinator`. Worker threads terminate, yet parser lifecycle cleanup could be improved.”

---

# Lesson 17 — Incremental Indexing, SHA-256, and Cache Correctness

## Context

Reparsing every unchanged file wastes work, but changing one declaration can affect how other files resolve. RepoPilot splits syntax incrementality from semantic correctness.

Open:

```text
src/main/java/com/repopilot/incremental/ChangeDetector.java
src/main/java/com/repopilot/incremental/ParseCache.java
src/main/java/com/repopilot/incremental/IncrementalIndexer.java
```

## Flow

```text
fresh repository scan
    ↓
compare path, size and last-modified with previous FileRecord
    ↓
if metadata changed, compare SHA-256
    ↓
classify added/modified/deleted/renamed/unchanged
    ↓
reuse ParseResult only when path+hash match
    ↓
fully parse changed files
    ↓
rebuild all symbols, all resolution and graph
```

Rename detection matches the hash of an added file with a deleted file.

## Exact answer

> “Incrementality is at whole-file parse-result level. I avoid re-running Tree-sitter on unchanged content, but I globally rebuild symbols and relationships because a type/import/signature change can alter resolution in untouched files. This trades maximum speed for graph correctness.”

## Why SHA-256?

> “Timestamps and file sizes are cheap first checks but can be misleading after checkout or copying. SHA-256 verifies content and also supports rename detection. Cryptographic collision resistance is more than required, but the implementation is simple and deterministic.”

## Alternatives

- mtime only: faster but unsafe across checkout/copy;
- rolling hash: faster but weaker and unnecessary at current scale;
- compiler incremental dependency graph: more precise invalidation but much more complex.

## Questions

### Q: Is Tree-sitter itself incremental here?

> “No. A changed file is fully parsed with `parseString(null, source)`. The cache only avoids parsing unchanged files.”

### Q: Why global re-resolution?

> “Because changing one public type or import can change the target of references in other files. Without a dependency-aware invalidation model, local-only updates could leave stale edges.”

---

# Lesson 18 — Jackson and Persistent Index Design

## Context

Commands such as `impact` should not reindex the repository every time. RepoPilot persists its symbol and graph model under `.repo-pilot`.

Open:

```text
src/main/java/com/repopilot/persist/IndexStore.java
src/main/java/com/repopilot/persist/IndexDtos.java
src/main/java/com/repopilot/persist/IndexPaths.java
```

Files:

```text
.repo-pilot/version
.repo-pilot/metadata.json
.repo-pilot/symbols.json
.repo-pilot/edges.json
.repo-pilot/files.json
.repo-pilot/parse-cache.json
```

## Jackson uses

- domain index serialization/deserialization;
- parse cache;
- benchmark `expected.json`;
- GitHub JSON;
- Groq request/API response/model JSON.

## Why separate DTOs?

> “Persistence DTOs decouple the wire format from domain classes. `IndexStore` validates the format version and checks every restored edge refers to existing nodes before trusting the index.”

## Atomic write logic

Data is written to a sibling `.tmp` file and moved into place with `ATOMIC_MOVE` when supported, falling back to replace. A damaged cache is discarded because it can be rebuilt; a corrupt main index fails loudly.

## What to say

> “I selected human-readable JSON for inspectability and easy debugging. The trade-off is larger monolithic files and slower large-scale loading compared with a binary or embedded database.”

## Alternatives

- Java serialization: fragile/version-coupled and unsafe for untrusted data;
- SQLite: indexed queries and transactional updates, better future scaling;
- Protobuf: compact/versioned but less human-readable;
- Neo4j: powerful graph query system but heavy for a local single-user CLI.

## Questions

### Q: Is JSON persistence transactional?

> “Each file replacement is atomic where supported, but the multi-file index is not one database transaction. Format validation reduces partial trust, but a manifest/generation directory swap would provide stronger all-or-nothing semantics.”

### Q: What happens when the index version changes?

> “`IndexStore` rejects unsupported format versions and instructs the user to reindex.”


---

# Lesson 19 — Groq: Optional Ranking, Not the Analysis Engine

## Context

A contributor may describe a change as “handle source task callback failure,” while code contains identifiers such as `commitTaskRecord`. Pure exact-name lookup may miss the intended starting symbol. RepoPilot first performs local lexical retrieval and can optionally use an LLM to rerank candidates.

Open:

```text
src/main/java/com/repopilot/ai/CandidateRetriever.java
src/main/java/com/repopilot/ai/NlTargetResolver.java
src/main/java/com/repopilot/ai/GroqClient.java
src/main/java/com/repopilot/ai/AiResponseValidator.java
src/main/java/com/repopilot/cli/AiOptions.java
```

## 19.1 Deterministic first stage

`CandidateRetriever` tokenizes camelCase and prose, removes stop words, and compares the request with symbol name, owner, annotations, return/field type, and parameter types. It excludes parameter symbols and returns at most 25 candidates for normal plan resolution.

## 19.2 Groq stage

The default model is:

```text
llama-3.3-70b-versatile
```

The request sends:

- sanitized description;
- candidate symbol ID;
- kind;
- signature;
- file and line;
- lexical score.

It does **not** send the complete repository source in the current path.

Expected JSON:

```json
{
  "targets": [
    {
      "symbol": "com.example.Service#process(Order)",
      "confidence": 0.91,
      "reason": "Matches order processing change"
    }
  ]
}
```

Validation requires:

- `targets` array;
- maximum five accepted results;
- symbol was offered;
- symbol exists in `SymbolIndex`;
- confidence is numeric and within 0–1;
- duplicates removed;
- reason sanitized.

## Modes

```text
--mode local  → no AI request
--mode auto   → Groq if key exists, otherwise local
--mode ai     → key required initially
--no-ai       → force local
```

Even in AI mode, API timeout/error/malformed output falls back to deterministic ranking after configuration validation.

## Exact speaking answer

> “AI is outside the source-of-truth pipeline. Local code creates the candidates and graph. Groq only reranks a fixed list of real symbols for the natural-language `plan` command. Its output is schema-, range-, and membership-validated. If it fails, lexical ranking continues. Groq is never called by `pr` analysis.”

## Why Groq?

> “It provides a low-latency OpenAI-compatible chat-completion API and structured JSON output for optional ranking. The architecture would benefit from a provider interface, but the current implementation is Groq-specific.”

## Alternatives

| Option | Benefit | Trade-off |
|---|---|---|
| Local embeddings | privacy and deterministic availability | model distribution/indexing complexity |
| BM25/Lucene only | no external service; strong lexical baseline | weaker semantic matching |
| local LLM via Ollama | source remains local | hardware/setup and model variability |
| OpenAI/Anthropic | capable hosted models | cost/privacy/provider dependency |
| no LLM | simplest and predictable | natural-language synonyms may rank worse |

## Security answer

> “The API key is read from an environment variable and is not accepted as a raw CLI argument or stored in `.repo-pilot`. Candidate membership validation—not prompt wording—is the strongest defense against an invented symbol. Symbol names and file paths can still be sensitive, so private repositories should use local mode unless policy allows external metadata.”

## Questions

### Q: Does the LLM affect correctness?

> “It can affect which existing symbols seed the natural-language plan, so it affects retrieval usefulness. It cannot change deterministic graph facts or insert an unknown node.”

### Q: Does Groq find files?

> “It ranks symbols. Files are obtained from those symbols and graph results. It does not invent expected filenames.”

### Q: Why not send all source?

> “That would increase privacy risk, token cost, prompt-injection surface, and context limits. The current design sends a bounded candidate list.”

### Q: Have you proven Groq improves accuracy?

> “No broad A/B benchmark exists. I should say it optionally reranks candidates, not claim statistically proven improvement.”

---

# Lesson 20 — Maven and the Build

## Context

The project combines Java/native parser bindings, JGit, Jackson, Spring Boot, Picocli, and tests. Maven provides dependency resolution and a repeatable lifecycle.

Open:

```text
pom.xml
```

Coordinates:

```text
com.repopilot:repo-pilot:1.1.0
Java release 21
Spring Boot parent 3.3.4
```

Main dependencies:

```text
spring-boot-starter
picocli
picocli-spring-boot-starter
jackson-databind
org.eclipse.jgit
Tree-sitter core + Java grammar
slf4j-nop
spring-boot-starter-test (test scope)
```

The Spring Boot Maven plugin creates the executable JAR with `RepoPilotApplication` as main class. Resource filtering writes the project version into `repo-pilot.properties`.

## What to say

> “Maven manages the Java 21 compile target, direct dependencies, JUnit test lifecycle, resource filtering, and Spring Boot fat-JAR packaging. The expected build is `mvn clean package`, which compiles, tests, and creates `target/repo-pilot.jar`.”

## Lifecycle explanation

```text
clean      removes target
compile    compiles main sources
 test      compiles/runs tests
package    builds and repackages executable JAR
```

## Alternatives

- Gradle: flexible and often faster incremental builds, but more build-script complexity.
- Bazel: strong hermetic scaling, excessive for this single module.
- direct `javac`: no convenient dependency/test/fat-JAR lifecycle.

## Honest build note

The v1.1 sandbox had no Maven/network, so main/test sources were compiled offline against extracted dependencies, a JUnit-compatible reflection harness ran the suite, and the existing Spring Boot JAR layout was rebuilt and smoke-tested. Before an interview, run the standard Maven build on your own machine and save the output.

## Questions

### Q: Why `slf4j-nop`?

> “It provides a no-operation SLF4J binding so library logging does not clutter CLI output. The code does not directly import it.”

### Q: Is every dependency used?

> “The major direct dependencies have real source or runtime use. Spring Boot is minimal rather than unused; `slf4j-nop` is runtime-only.”

---

# Lesson 21 — Testing Strategy and Correct Metrics

## Recorded truth

```text
22 test classes
143 tests passed
0 failed
3.704 seconds in v1.1 offline audit harness
```

Do not say 21 milliseconds.

Open:

```text
src/test/java/com/repopilot/
```

Important suites:

```text
TreeSitterParserTest
SymbolIndexTest
SemanticResolutionTest
ImpactEngineTest
DiffAnalysisTest
JenkinsPr23905RegressionTest
IncrementalIndexingTest
ConcurrencyTest
SpringAnalysisTest
AiTargetResolutionTest
SecurityTest
CliEndToEndTest
BenchmarkRunnerTest
```

## How to explain the test pyramid

> “Most tests are unit or small integration-style tests using temporary repositories. Parser tests validate syntax extraction; resolver tests cover the resolution ladder and ambiguity; graph tests cover radius, cycles, confidence, and test filtering; diff tests cover hunk mapping and file types; end-to-end CLI tests run workflows. External AI success is not required because failure and model output are controlled.”

## Representative tests

- malformed source is reported, not fatal;
- overload selected by arity/type hints;
- same simple type name becomes ambiguous without import;
- cyclic graph terminates;
- index strategies produce identical digest;
- corrupt index fails with actionable message;
- symlink/path traversal rejected;
- injected prompt cannot introduce a symbol;
- Jenkins-derived regression avoids unrelated class fallback;
- Kafka-derived issue fixture retrieves expected files within top five.

## Missing tests you must admit

- broad real PR corpus;
- live Groq/GitHub CI contract tests;
- 80,000-file load test;
- mutation/fuzz testing;
- full v1.1 Maven/Surefire log from normal environment;
- controlled human time-saving study;
- compiler-classpath semantics because not implemented.

## Questions

### Q: Unit or integration tests?

> “Mixed. Many classes are unit-tested, while CLI, temporary repository indexing, Tree-sitter native parsing, JGit-style revision mapping, persistence, and benchmark cases are integration-style.”

### Q: Why no live API calls?

> “Unit tests should be deterministic and not depend on credentials, rate limits, or network. A separate optional contract-test profile would be appropriate.”

### Q: Does 143 passing tests prove accuracy?

> “No. It proves covered behavior against fixtures. External validity requires a larger independent real-PR dataset and false-positive/false-negative analysis.”

---

# Lesson 22 — Benchmark and Efficiency Claims

## Kafka observed run

```text
798 Java files
17,816 symbols
37,441 edges
34,467 unresolved references
21,515 ms indexing duration
```

## What exactly was timed?

`IndexStats.millis` covers scan/change detection, parse/cache work, symbol extraction, semantic resolution, optional Spring analysis, graph build, file fingerprints, and graph digest. It ends before `IndexStore.save` and `ParseCache.save`.

It does not include:

- GitHub API time;
- JGit PR snapshot work;
- later BFS query;
- human contribution time;
- separate phase timings.

## Safe speaking answer

> “On one recorded Kafka checkout, RepoPilot indexed 798 Java files into 17,816 symbols and 37,441 edges in 21.5 seconds. It was one observed run, not a formal average, and I have not preserved complete hardware/JVM/cold-cache metadata. Therefore I present it as a functional scale test, not a rigorous benchmark.”

## “Drastically reduced contribution time” correction

Say:

> “It reduced the initial search space from hundreds of Java files to a small evidence-backed set for that investigation.”

Do not say:

> “It reduced contribution time by 90%.”

No controlled human-time experiment exists.

## Bundled benchmark

Five small structural cases plus one Kafka PR-1402-derived issue fixture report impact precision/recall/F1. The Kafka fixture also reports issue-to-file Recall@5 of 1.0 for two expected files. That fixture is compact and not the full Kafka repository.

## Jenkins qualification

`JenkinsPr23905RegressionTest` constructs base/head fixture source plus Jelly/SCSS/SVG resources. It checks exact members and structural observations. It is not a full Jenkins performance run.

## Questions

### Q: Which phase is bottleneck?

> “The code only records total indexing time, so exact attribution requires profiling. Parsing and global resolution/graph construction are likely candidates, but I should not claim one without JFR or per-phase timers.”

### Q: How would you benchmark properly?

> “Fix hardware/JDK/options; separate cold and warm runs; run multiple forks/iterations; report median and percentiles; record CPU, heap and GC; time scanning, parsing, extraction, resolution, graph, persistence and query separately; use JFR/async-profiler and a representative real corpus.”

---

# Lesson 23 — Java 21 Features Actually Used

## Used

1. **Records** for immutable domain data.
2. **Virtual threads** as an optional indexing strategy.
3. **Switch expressions/arrow cases** throughout parsing and classification.
4. **Text blocks** for Groq prompt and tests.
5. **Modern try-with-resources**, including `ExecutorService` lifecycle.

## Not used

- sealed class hierarchy;
- record patterns;
- structured concurrency;
- scoped values;
- a major pattern-matching switch design.

## What to say

> “Java 21 is not only a version label. Records reduce boilerplate for many immutable evidence objects, and virtual threads are available for the parser executor. I also use switch expressions and text blocks. I do not claim unused Java 21 features.”

## Follow-up: Why records?

> “Most pipeline objects are immutable facts passed across phases. Records give value-based equality, accessors, concise constructors, and work well with Jackson. Compact constructors defensively copy collections and validate ranges.”

---

# Lesson 24 — OOP and Design Patterns Without Buzzword Inflation

## Actual principles

- encapsulation in clients/parsers/stores/engines;
- composition in `IndexingService` and command flows;
- immutable value objects through records;
- polymorphism through `Callable<Integer>`, `AutoCloseable`, Spring runner interfaces;
- constructor injection in many classes;
- minimal inheritance, mainly custom exceptions.

## Defensible patterns

```text
Pipeline                 IndexingService
Adapter/wrapper          GitHubClient, GitService, GroqClient, TreeSitterParser
Enum-based strategy      ExecutionStrategy + IndexingExecutor
Builder/construction     GraphBuilder and internal diff builders
Facade/application API   IndexingService, ImpactEngine
DTO separation           IndexDtos
```

## What not to claim

Do not call every class a design pattern. There is no rich abstract factory, observer system, MVC architecture, or project-defined repository interface.

## Questions

### Q: Where is dependency inversion?

> “It is limited. Several classes receive collaborators through constructors, but command classes instantiate concrete services and there are few project-defined interfaces. Introducing interfaces for GitHub, AI, parsing, and persistence would improve testability/provider substitution.”

### Q: Why records instead of mutable POJOs?

> “Evidence should not change after a phase produces it. Immutable records prevent accidental mutation and make deterministic comparison/testing easier.”

---

# Lesson 25 — Error Handling and Failure Engineering

## Error categories

```text
usage/configuration error → exit 2
runtime/network/index error → exit 1
ambiguous target → exit 3
missing index → exit 4
success → exit 0
```

Open:

```text
src/main/java/com/repopilot/cli/CommandSupport.java
src/main/java/com/repopilot/cli/ExitCodes.java
```

## Design idea

> “Expected uncertainty is data; unexpected failure is an exception. An unresolved call becomes `UnresolvedReference`, while a corrupt index or unavailable GitHub request becomes an actionable command error.”

Examples:

- malformed file → partial/failed `ParseResult`, continue;
- ambiguous overload → candidate edges/status;
- missing base SHA → unverified changed targets, no exact mapping;
- corrupt index → fail loudly and suggest clean/reindex;
- Groq failure → deterministic fallback;
- GitHub failure → command error;
- one concurrent parse task fails → mapped result, other tasks continue.

## Questions

### Q: Why not throw on every unresolved call?

> “Large repositories naturally contain external or dynamic references. Treating each as fatal would make the tool unusable. Storing them as evidence lets analysis continue while exposing completeness risk.”

### Q: Why does Groq fail open but GitHub fail closed?

> “Groq is optional ranking and has a deterministic local substitute. A remote GitHub PR request has no corresponding PR data if the request fails, so pretending to continue would analyze the wrong thing.”


---

# Lesson 26 — Security: Defending Against Hostile Repositories and Secrets

## Threat model context

RepoPilot reads repositories that may be untrusted and optionally sends metadata to hosted APIs. The main threats are path escape, symlinks, resource exhaustion, exposed tokens, prompt injection, and vulnerable parsing dependencies.

## Implemented controls

```text
SafePath                     rejects absolute/NUL/escaping paths
RepositoryScanner            does not follow symlinks
ResourceLimits               caps depth/files/bytes
FileFilter                   excludes binary/generated/build files
GitRevisionSnapshotService   skips Git symlink and submodule entries
AiOptions                    accepts key environment-variable name only
PromptSanitizer              removes obvious injection markers/control chars
AiResponseValidator          requires offered + indexed symbol membership
HTTP clients                 use request/connect timeouts
IndexStore                   validates format and edge endpoints
```

## Exact answer

> “I use defense in depth. Repository files are treated as data, never compiled or executed. Paths are normalized and constrained to root, symbolic links are not followed, and resource limits protect the native parser. API keys come from environment variables. For AI, prompt sanitization helps, but the real security boundary is that model output must match a symbol already offered from the local index.”

## What is not handled

- dependency vulnerability scanning/SBOM;
- OS keychain or encrypted token store;
- certificate pinning;
- sandbox process around native parsing;
- strict HTTP response streaming limits;
- formal prompt-injection proof;
- fine-grained private-source policy;
- file-permission management for `.repo-pilot`.

## Questions

### Q: Can source comments inject instructions into Groq?

> “Candidate retrieval does not send full comments or source bodies. The description/candidate metadata is sanitized and fenced, and an AI-selected ID must be in both the offered set and index. Prompt injection could still affect ranking among valid candidates, so local mode is the strongest privacy option.”

### Q: Where are secrets stored?

> “They are not persisted by RepoPilot. `GITHUB_TOKEN` and the selected Groq key variable are read from the process environment. Environment variables have their own OS/process visibility risks.”

---

# Lesson 27 — Limitations and How to Defend Them

A mature answer does not hide limitations. Use this pattern:

```text
limitation → architectural reason → user-visible handling → future improvement
```

## External libraries and JDK

> “The symbol table contains repository source only, so JDK and dependency declarations are unresolved. I classify external gaps separately so they do not look like missing project files. Future work would add classpath/JAR stubs or compiler attribution.”

## Reflection and dynamic loading

> “A target chosen from strings or runtime configuration cannot generally be proven statically. RepoPilot retains unresolved/dynamic evidence rather than inventing an edge. Runtime traces or string/data-flow analysis would be needed.”

## Generated code and Lombok

> “Annotation processors are not executed, so generated methods are absent. A compiler-based index or delombok/generated-source integration would be required.”

## Field reads/writes

> “RepoPilot models field declaration types but does not create read/write-use edges. Therefore changing a field may not discover every method that accesses it. Adding identifier-binding and access analysis is future work.”

## Generics/overloads

> “Generic support is limited to erasure and declared upper bounds. Overloads use arity and simple type hints rather than full Java conversion and specificity rules.”

## Multi-language repositories

> “Only Java enters the semantic graph. A few resource types produce unverified structural observations, not cross-language dependency edges.”

## Deleted methods

> “The base declaration can be labeled deleted, but it no longer exists in the head graph. Historical reverse impact from the base graph and mapping those dependents into head symbols is not implemented.”

## False positives and negatives

- ambiguity/framework heuristics can add false positives;
- unresolved/project-local/dynamic relations can cause false negatives;
- radius limits intentionally omit farther nodes;
- confidence is heuristic, not calibrated.

## Interview question: Why release an incomplete analyzer?

> “The goal is not compiler replacement. A useful contributor tool can provide a transparent starting set if it exposes uncertainty and never presents incomplete analysis as complete. The output explicitly distinguishes exact, uncertain, unresolved, and file-level evidence.”

---

# Lesson 28 — Scaling from 798 to 80,000 Files

## Current behavior

At 80,000 Java files:

- scanning may still fit the 200,000-file/4-GiB limits;
- parser tasks can be bounded-parallel;
- candidate retrieval scans every symbol;
- all symbols and edges live in memory and duplicated adjacency structures;
- every incremental run globally rebuilds semantic resolution and graph;
- index/cache JSON files become large and monolithic;
- verified PR analysis indexes both base and head snapshots;
- BFS itself remains proportional to the reachable subgraph, but graph load/build dominates.

## Senior interview answer

> “The current implementation is not proven for 80,000 Java files. Parsing is parallelizable, but global resolution, in-memory adjacency, repeated list sorting, monolithic JSON, and dual snapshot indexing become bottlenecks. Resource limits prevent unbounded behavior but do not guarantee acceptable latency.”

## Improvement plan

1. add per-phase profiling and real large-repository load tests;
2. batch-build and sort indexes rather than sorting after every symbol insertion;
3. shard parse/symbol/edge storage by module or file;
4. use SQLite/RocksDB or compact binary storage;
5. build dependency-aware invalidation for incremental resolution;
6. cache commit indexes by Git tree ID;
7. compare only changed modules/subgraphs where safe;
8. parallelize resolution with immutable symbol tables and deterministic merging;
9. use a compiler/JDT service for classpath semantics;
10. preserve bounded candidate context for AI.

## Questions

### Q: Would you choose Neo4j?

> “Not automatically. The dominant access is local adjacency traversal and deterministic persistence. An embedded indexed store may be simpler. I would benchmark SQLite/RocksDB/custom compressed adjacency against Neo4j before adding a server dependency.”

### Q: How would commit caching work?

> “The Git tree ID is content-addressed. I could key a reusable immutable index by tree ID, so the same base commit is not rescanned for every PR.”

---

# Lesson 29 — Exact End-to-End Code Walkthrough

Use this during a screen-sharing interview.

## Step 1: Entry

Open `RepoPilotApplication.java`.

Say:

> “This is the executable boundary. Spring runs without a web server, Picocli parses arguments, and the returned command code becomes the process exit code.”

## Step 2: Command routing

Open `cli/RootCommand.java` and `cli/PrCommand.java`.

Say:

> “The root registers subcommands. `PrCommand` validates one of three sources: GitHub PR, diff file, or local working tree. It then produces `List<FileDiff>` and a `PrAnalysisResult`.”

## Step 3: Remote facts

Open `github/GitHubClient.java`.

Say:

> “Two GET requests retrieve selected metadata and unified diff. The client has explicit authentication, timeout, status, and rate-limit errors. It does not fetch comments or clone code.”

## Step 4: Git verification

Open `git/GitRevisionSnapshotService.java`.

Say:

> “JGit resolves each revision as a commit, walks its immutable tree, safely copies blobs to a temp directory, records commit/tree provenance, and indexes that exact snapshot. It never switches the user's branch.”

## Step 5: Scan/parse

Open `scan/RepositoryScanner.java`, then `parse/TreeSitterParser.java`.

Say:

> “The scanner produces a safe sorted Java file set. Tree-sitter parses a CST and I extract selected declarations/calls with byte and line ranges. Parser state is thread-local.”

## Step 6: Symbols

Open `symbol/SymbolExtractor.java` and `SymbolId.java`.

Say:

> “Parsed declarations become stable IDs. Owner plus erased-as-written parameter signature keeps overloads separate.”

## Step 7: Semantics

Open `resolve/SymbolResolver.java`, `TypeResolver.java`, and `MethodResolver.java`.

Say:

> “The resolver uses all symbols together to bind imports, receivers, types, methods, inheritance, and overloads. It emits a `Reference` carrying resolution status, confidence, level, and evidence.”

## Step 8: Graph

Open `graph/GraphBuilder.java` and `CodeGraph.java`.

Say:

> “Only real symbols become nodes. Resolved references become directed edges; ambiguous ones become uncertain candidate edges; no-target references stay separate. Both outgoing and incoming adjacency are stored.”

## Step 9: Changed targets

Open `git/RevisionAwareChangedTargetLocator.java`.

Say:

> “This validates hunk text against both revisions and maps old and new ranges separately. Same symbol ID is modified; side-only IDs are additions/deletions. Missing proof gives file-level status rather than a guessed method.”

## Step 10: Impact

Open `impact/ImpactEngine.java`.

Say:

> “Exact head symbols seed multi-source reverse BFS. The best-path map handles cycles and chooses shortest, certain, high-confidence evidence. Incoming edges reveal dependents.”

## Step 11: Output

Open `cli/Ui.java`.

Say:

> “The result is separated into direct, indirect-by-radius, test, uncertain, and unresolved coverage sections. Full paths are optional through `--explain`.”

## Step 12: Optional plan AI

Open `ai/NlTargetResolver.java` and `AiResponseValidator.java`.

Say:

> “This is a different path for unimplemented changes. Local retrieval finds real candidate symbols; Groq may rerank them; strict membership validation protects the graph.”

---

# Lesson 30 — Complete Mock Interview

## Opening

### Interviewer: Tell me about RepoPilot.

> “RepoPilot is a Java CLI for contributors investigating unfamiliar Java repositories. It maps revision-verified PR diff hunks to declarations, creates a repository-local dependency graph, and uses reverse BFS to produce a radius-bounded set of dependent production and test symbols with evidence. It also supports natural-language planning, where Groq may rerank existing candidates but never creates graph facts.”

### Interviewer: What did you personally build?

> “I worked across the complete pipeline: safe scanning, Tree-sitter extraction, stable symbol modeling, approximate semantic resolution, graph construction, diff/revision mapping with JGit, impact traversal, persistence, optional AI validation, CLI commands, and regression tests.”

## Parsing deep dive

### Q: Why Tree-sitter?

> “It tolerates incomplete repositories and preserves exact ranges without requiring a successful Maven classpath. The cost is weaker semantics than a compiler, which is why I built a separate resolver and expose unresolved coverage.”

### Q: What does Tree-sitter return?

> “A concrete syntax tree of `TSNode`s owned by a `TSTree`. I traverse grammar node types and slice original UTF-8 bytes by node offsets.”

### Q: How do you keep native tree memory safe?

> “Nodes are views over tree memory, so extraction keeps the `TSTree` reachable and calls `Reference.reachabilityFence(tree)` before leaving the parse scope.”

## Symbol/resolution deep dive

### Q: How do you uniquely identify methods?

> “FQN owner, method name, and erased-as-written parameter types. Constructors use `<init>`, and parameters append `::name`.”

### Q: How do you resolve a call?

> “I resolve receiver type from enclosing context, parameters, locals, fields, types, chains, or `this/super`; then search the receiver's supertype closure, filter methods by name and arity, and apply known argument hints. Multiple remaining targets are ambiguous.”

### Q: Why not complete Java semantics?

> “That would require compiler attribution, classpath dependencies, generic substitutions, accessibility and conversion rules. RepoPilot intentionally trades completeness for build-independent operation.”

## Git deep dive

### Q: How do you know line 100 belongs to a method?

> “A validated diff hunk provides old/new line ranges. I intersect removed ranges with base symbol locations and added ranges with head locations, selecting member declarations and refusing source mismatch.”

### Q: Why two indexes?

> “A deletion cannot be found in head source, and an addition cannot be found in base source. Separate immutable indexes preserve revision meaning.”

### Q: Does JGit fetch PR commits?

> “No. The user must fetch them. JGit only resolves and reads objects already in local `.git`.”

## Graph deep dive

### Q: Why reverse BFS?

> “Edges point dependent→dependency. If B changes, incoming(B) gives symbols that depend on B. BFS gives shortest radius and a queue-based layer order.”

### Q: How do you handle multiple paths?

> “One best result per symbol, preferring shorter radius, certain classification, higher confidence, then deterministic path order.”

### Q: How do tests appear?

> “Test source is in the same graph. Reverse traversal can reach a test method that calls the changed production method; path/name heuristics mark it test code.”

## AI deep dive

### Q: Why call it AI-assisted?

> “I would avoid making that the main project identity. AI is an optional ranking adapter only for `plan`. Deterministic analysis remains the source of truth.”

### Q: How do you stop hallucination?

> “The model can choose only from up to 25 offered IDs; validation checks offered membership and `SymbolIndex` membership, confidence range, shape, count, and duplicates.”

## Performance/testing

### Q: Did it really take 21.5 seconds?

> “One Kafka run printed 21,515 milliseconds for indexing 798 Java files. It was not a multi-run median and the exact machine metadata needs to be re-recorded.”

### Q: 143 tests in 21 ms?

> “No. The correct v1.1 audit result is 143 passes in about 3.7 seconds. I corrected that resume statement.”

## Limitations

### Q: What is the largest correctness gap?

> “Repository-local approximate resolution without classpath or data flow. Project-local unresolved and dynamic calls can hide affected symbols, especially with frameworks, reflection, generated code, and complex expressions.”

### Q: What would you improve first?

> “First, build a larger independent real-PR benchmark so optimization follows measured errors. Technically, classpath-aware semantic resolution and complete local-working-tree verification would address major false negatives and workflow gaps.”

---

# Lesson 31 — Rapid Follow-Up Questions and Exact Answers

1. **What is a CST?**  
   A grammar-complete syntax tree preserving concrete source structure/ranges; it is not type-attributed semantics.

2. **What is static analysis?**  
   Reasoning about code without executing it. Here it means source-based declarations and repository-local dependency relationships.

3. **What is semantic resolution?**  
   Binding a written reference such as `service.run()` to an indexed declaration using scope/type/import rules.

4. **What is a graph node?**  
   A real indexed symbol.

5. **What is an edge?**  
   A typed directed dependency with evidence.

6. **What does incoming adjacency do?**  
   Finds dependents of a changed target efficiently.

7. **Why BFS over DFS?**  
   Radius requires shortest dependency distance.

8. **Is BFS weighted?**  
   Distance is unweighted edge count. Confidence is used as a secondary path quality measure, not Dijkstra weight.

9. **What is radius zero?**  
   The selected or exact changed symbol.

10. **What is `DIRECTLY MODIFIED`?**  
    Radius-zero targets, not all files that must be edited.

11. **Can cycles exist?**  
    Yes; best-path state prevents infinite traversal.

12. **What is unresolved?**  
    Source relationship exists, but no indexed target can be selected.

13. **What is ambiguous?**  
    Several indexed candidates remain and RepoPilot refuses to choose one as fact.

14. **What is dynamic?**  
    Runtime behavior determines the target, such as some lambda/reflection/proxy cases.

15. **What is JGit?**  
    A pure Java Git implementation used here for local repository objects, revisions, trees, and diffs.

16. **What is GitHub API used for?**  
    PR title/state/base/head and unified diff only.

17. **What is Jackson?**  
    JSON binding used for persistence, APIs, model validation, and benchmark definitions.

18. **What is Picocli?**  
    Annotation-driven Java CLI parser/dispatcher.

19. **What is Spring Boot doing?**  
    Non-web application lifecycle and Picocli factory integration.

20. **What is Groq doing?**  
    Optional candidate-symbol reranking in `plan` only.

21. **What is incremental?**  
    Unchanged file parse results are reused by hash; semantic graph is globally rebuilt.

22. **What is deterministic?**  
    Same source/options produce sorted equivalent symbol/graph output independent of parser completion order.

23. **Why SHA-256?**  
    Content verification, cache correctness, rename detection, and graph digest.

24. **Does it compile target code?**  
    No.

25. **Does it run tests?**  
    No.

26. **Does it support Kotlin?**  
    No semantic Kotlin graph.

27. **Does it understand Lombok?**  
    Not generated members unless generated source is explicitly present and accepted.

28. **Does it resolve JDK calls?**  
    No JDK source/classpath index; they are classified external gaps.

29. **Can AI alter the graph?**  
    No.

30. **Can it guarantee completeness?**  
    No; it reports coverage and risk gaps.

---

# Lesson 32 — Your Final Resume and Interview Claim

Use this version:

> **RepoPilot – Contributor Saathi | Java 21, Tree-sitter, JGit, Picocli, Jackson, Spring Boot, JUnit 5, Groq, Graph/BFS**  
> Built a Java CLI that maps revision-verified PR diff hunks to Java symbols and returns radius-bounded dependent production/test code with evidence paths. Implemented repository-local semantic resolution, persistent and incremental indexing, bounded parsing concurrency, and optional validated Groq reranking. On one Kafka run, indexed 798 Java files into 17,816 symbols and 37,441 edges in 21.5s; v1.1 audit recorded 143 passing tests in 3.7s.

If space is very limited:

> Built a Java CLI that maps verified PR diff hunks to symbols and traces dependent code/tests using Tree-sitter, semantic resolution, and reverse BFS; indexed 798 Kafka Java files into 17,816 symbols and 37,441 edges in one 21.5s run.

---

# Lesson 33 — Seven-Day Study Plan

## Day 1 — Story and architecture

- Speak the 30-second and 2-minute answers five times.
- Draw all packages from memory.
- Explain facts vs heuristics vs AI.

## Day 2 — Parser and symbols

- Open `TreeSitterParser`, `SymbolExtractor`, and `SymbolId`.
- Explain CST/AST distinction.
- Create three overload IDs by hand.

## Day 3 — Resolution

- Trace an unqualified call, field receiver, imported receiver, inherited method, and ambiguous overload.
- Memorize limitations, not implementation line numbers.

## Day 4 — Graph and BFS

- Draw five-node graphs.
- Run reverse BFS manually.
- Explain confidence, cycles, tests, and override propagation.

## Day 5 — Git/PR

- Explain GitHub vs local Git vs JGit.
- Trace added, removed, modified, renamed, and stale hunks.
- Explain why local working-tree mode is partial.

## Day 6 — Engineering

- Review concurrency, cache, JSON persistence, security, Spring, Picocli, Maven, and Groq.
- Answer “why this tool and what alternative?” for each.

## Day 7 — Mock interview

- Record a five-minute walkthrough.
- Answer 30 rapid questions.
- Rerun Maven tests and one documented benchmark.
- Correct any metric you cannot reproduce.

---

# Final Teacher Checklist

You are interview-ready only if you can explain, without notes:

- the user problem before naming technologies;
- the exact difference between PR mode and plan mode;
- CST → domain parse facts → symbols → resolution → graph;
- old/base and new/head diff mapping;
- edge direction and reverse BFS;
- radius zero/one and test classification;
- JGit versus GitHub API;
- Groq's constrained role;
- concurrency and incremental boundaries;
- three major false-negative sources;
- exact honest test/performance metrics;
- one design trade-off and one improvement for every major tool.

> **Final speaking principle:** A strong candidate does not defend the project by pretending it is complete. A strong candidate explains where evidence ends, where heuristics begin, why each trade-off was made, and how the next version would be measured before being built.
