package com.repopilot.incremental;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.repopilot.parse.ParseResult;
import com.repopilot.persist.IndexPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores the syntax facts of every indexed file, keyed by content hash.
 *
 * <p>Without this cache an incremental run would still have to re-parse untouched files in order to
 * resolve references across the whole repository. With it, only changed files hit Tree-sitter.
 */
public class ParseCache {

    /** @param sha256 content hash the parse result belongs to */
    public record Entry(String sha256, ParseResult result) {
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public static Path fileFor(Path root) {
        return new IndexPaths(root).directory().resolve("parse-cache.json");
    }

    public void put(String path, String sha256, ParseResult result) {
        entries.put(path, new Entry(sha256, result));
    }

    public void remove(String path) {
        entries.remove(path);
    }

    /** Returns the cached parse result when the stored hash matches the current content. */
    public ParseResult get(String path, String sha256) {
        Entry entry = entries.get(path);
        return entry != null && entry.sha256().equals(sha256) ? entry.result() : null;
    }

    public int size() {
        return entries.size();
    }

    public void save(Path root) throws IOException {
        Path target = fileFor(root);
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        mapper.writeValue(temp.toFile(), entries);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Loads the cache; a damaged cache is simply dropped, because it can always be rebuilt. */
    public static ParseCache load(Path root) {
        ParseCache cache = new ParseCache();
        Path file = fileFor(root);
        if (!Files.isRegularFile(file)) {
            return cache;
        }
        try {
            Map<String, Entry> loaded = cache.mapper.readValue(file.toFile(),
                    cache.mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Entry.class));
            cache.entries.putAll(loaded);
        } catch (IOException | RuntimeException e) {
            cache.entries.clear();
        }
        return cache;
    }
}
