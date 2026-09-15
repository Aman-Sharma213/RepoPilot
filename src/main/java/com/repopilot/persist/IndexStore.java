package com.repopilot.persist;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.repopilot.cli.VersionProvider;
import com.repopilot.graph.CodeGraph;
import com.repopilot.graph.EdgeType;
import com.repopilot.graph.GraphEdge;
import com.repopilot.graph.GraphNode;
import com.repopilot.graph.UnresolvedReference;
import com.repopilot.incremental.FileRecord;
import com.repopilot.index.IndexStats;
import com.repopilot.index.RepoIndex;
import com.repopilot.parse.SourceLocation;
import com.repopilot.persist.IndexDtos.EdgeDto;
import com.repopilot.persist.IndexDtos.FileDto;
import com.repopilot.persist.IndexDtos.GraphDto;
import com.repopilot.persist.IndexDtos.LocationDto;
import com.repopilot.persist.IndexDtos.MetadataDto;
import com.repopilot.persist.IndexDtos.StatsDto;
import com.repopilot.persist.IndexDtos.SymbolDto;
import com.repopilot.persist.IndexDtos.UnresolvedDto;
import com.repopilot.resolve.ResolutionLevel;
import com.repopilot.resolve.ResolutionStatus;
import com.repopilot.symbol.Symbol;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolIndex;
import com.repopilot.symbol.SymbolKind;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the index under {@code .repo-pilot/}.
 *
 * <p>Writes go to temporary files and are moved into place, so an interrupted {@code index} run
 * cannot leave a half-written index behind. Reads validate the format version and fail loudly with
 * {@link IndexCorruptException} instead of silently returning a partial graph.
 */
public class IndexStore {

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public void save(RepoIndex index) throws IOException {
        IndexPaths paths = new IndexPaths(index.root());
        Files.createDirectories(paths.directory());

        List<SymbolDto> symbols = new ArrayList<>();
        for (Symbol symbol : index.symbols().all()) {
            symbols.add(new SymbolDto(symbol.id().value(), symbol.kind().name(), symbol.simpleName(),
                    symbol.packageName(), symbol.ownerFqn(), symbol.parameterTypes(), symbol.type(),
                    symbol.superTypes(), List.copyOf(symbol.modifiers()), symbol.annotations(),
                    symbol.typeParameters(), toDto(symbol.location())));
        }
        List<EdgeDto> edges = new ArrayList<>();
        for (GraphEdge edge : index.graph().edges()) {
            edges.add(new EdgeDto(edge.source().value(), edge.target().value(), edge.type().name(),
                    toDto(edge.location()), edge.confidence(), edge.status().name(), edge.level().name(),
                    edge.evidence()));
        }
        List<UnresolvedDto> unresolved = new ArrayList<>();
        for (UnresolvedReference reference : index.graph().unresolvedReferences()) {
            unresolved.add(new UnresolvedDto(reference.source().value(), reference.type().name(),
                    reference.status().name(), reference.rawText(), reference.reason(),
                    toDto(reference.location())));
        }
        List<FileDto> files = index.files().values().stream()
                .map(f -> new FileDto(f.path(), f.size(), f.lastModified(), f.sha256(), f.symbolCount()))
                .sorted(java.util.Comparator.comparing(FileDto::path))
                .toList();
        IndexStats stats = index.stats();
        MetadataDto metadata = new MetadataDto(IndexPaths.FORMAT_VERSION, index.root().toString(),
                Instant.now().toString(), VersionProvider.version(),
                new StatsDto(stats.files(), stats.parsedFiles(), stats.filesWithSyntaxErrors(), stats.symbols(),
                        stats.nodes(), stats.edges(), stats.unresolvedReferences(), stats.millis(),
                        stats.digest(), stats.mode()));

        writeAtomically(paths.versionFile(), String.valueOf(IndexPaths.FORMAT_VERSION));
        writeJsonAtomically(paths.symbolsFile(), symbols);
        writeJsonAtomically(paths.edgesFile(), new GraphDto(edges, unresolved));
        writeJsonAtomically(paths.filesFile(), files);
        writeJsonAtomically(paths.metadataFile(), metadata);
    }

    public boolean exists(Path root) {
        return new IndexPaths(root).hasIndex();
    }

    public RepoIndex load(Path root) {
        IndexPaths paths = new IndexPaths(root);
        if (!paths.hasIndex()) {
            throw new IndexCorruptException("No index found in " + paths.directory() + ". Run: repo-pilot index");
        }
        try {
            String version = Files.readString(paths.versionFile()).trim();
            if (!version.equals(String.valueOf(IndexPaths.FORMAT_VERSION))) {
                throw new IndexCorruptException("Index format version " + version + " is not supported by this "
                        + "build (expected " + IndexPaths.FORMAT_VERSION + "). Run: repo-pilot index");
            }
            List<SymbolDto> symbolDtos = mapper.readValue(paths.symbolsFile().toFile(),
                    mapper.getTypeFactory().constructCollectionType(List.class, SymbolDto.class));
            GraphDto graphDto = mapper.readValue(paths.edgesFile().toFile(), GraphDto.class);
            List<FileDto> fileDtos = mapper.readValue(paths.filesFile().toFile(),
                    mapper.getTypeFactory().constructCollectionType(List.class, FileDto.class));
            MetadataDto metadata = mapper.readValue(paths.metadataFile().toFile(), MetadataDto.class);

            SymbolIndex symbols = new SymbolIndex();
            for (SymbolDto dto : symbolDtos) {
                symbols.add(new Symbol(new SymbolId(dto.id()), SymbolKind.valueOf(dto.kind()), dto.simpleName(),
                        nullToEmpty(dto.packageName()), nullToEmpty(dto.ownerFqn()), orEmpty(dto.parameterTypes()),
                        dto.type(), orEmpty(dto.superTypes()), java.util.Set.copyOf(orEmpty(dto.modifiers())),
                        orEmpty(dto.annotations()), orEmpty(dto.typeParameters()), fromDto(dto.location())));
            }
            CodeGraph graph = new CodeGraph();
            for (Symbol symbol : symbols.all()) {
                graph.addNode(GraphNode.of(symbol));
            }
            for (EdgeDto dto : graphDto.edges()) {
                GraphEdge edge = new GraphEdge(new SymbolId(dto.source()), new SymbolId(dto.target()),
                        EdgeType.valueOf(dto.type()), fromDto(dto.location()), dto.confidence(),
                        ResolutionStatus.valueOf(dto.status()), ResolutionLevel.valueOf(dto.level()),
                        dto.evidence());
                if (!graph.addEdge(edge)) {
                    throw new IndexCorruptException("Edge references an unknown symbol: " + dto.source()
                            + " -> " + dto.target());
                }
            }
            for (UnresolvedDto dto : graphDto.unresolved()) {
                graph.addUnresolved(new UnresolvedReference(new SymbolId(dto.source()),
                        EdgeType.valueOf(dto.type()), ResolutionStatus.valueOf(dto.status()), dto.rawText(),
                        dto.reason(), fromDto(dto.location())));
            }
            graph.sortDeterministically();

            Map<String, FileRecord> files = new LinkedHashMap<>();
            for (FileDto dto : fileDtos) {
                files.put(dto.path(), new FileRecord(dto.path(), dto.size(), dto.lastModified(), dto.sha256(),
                        dto.symbolCount()));
            }
            StatsDto s = metadata.stats();
            IndexStats stats = new IndexStats(s.files(), s.parsedFiles(), s.filesWithSyntaxErrors(), s.symbols(),
                    s.nodes(), s.edges(), s.unresolvedReferences(), s.millis(), s.digest(), s.mode());
            return new RepoIndex(root, symbols, graph, files, stats);
        } catch (IndexCorruptException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IndexCorruptException("The index in " + paths.directory() + " is unreadable ("
                    + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage())
                    + "). Run: repo-pilot clean && repo-pilot index", e);
        }
    }

    public void clean(Path root) throws IOException {
        Path directory = new IndexPaths(root).directory();
        if (!Files.exists(directory)) {
            return;
        }
        try (var walk = Files.walk(directory)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public MetadataDto readMetadata(Path root) {
        IndexPaths paths = new IndexPaths(root);
        try {
            return mapper.readValue(paths.metadataFile().toFile(), MetadataDto.class);
        } catch (IOException e) {
            throw new IndexCorruptException("Cannot read " + paths.metadataFile(), e);
        }
    }

    private void writeJsonAtomically(Path target, Object value) throws IOException {
        writeAtomically(target, mapper.writeValueAsString(value));
    }

    private void writeAtomically(Path target, String content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static LocationDto toDto(SourceLocation location) {
        return new LocationDto(location.file(), location.startLine(), location.endLine(), location.startColumn(),
                location.endColumn(), location.startByte(), location.endByte());
    }

    private static SourceLocation fromDto(LocationDto dto) {
        if (dto == null) {
            throw new IndexCorruptException("Missing source location in the persisted index");
        }
        return new SourceLocation(dto.file(), dto.startLine(), dto.endLine(), dto.startColumn(), dto.endColumn(),
                dto.startByte(), dto.endByte());
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
