package com.repopilot.git;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic resource inspection. Findings are observations, never Java graph edges. */
public class NonJavaChangeAnalyzer {
    private static final Set<String> SUPPORTED = Set.of("jelly", "scss", "css", "svg", "xml", "yaml", "yml",
            "json", "properties");
    private static final Pattern EL = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern ATTR = Pattern.compile("(?:name|src|href|page|id)\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern SELECTOR = Pattern.compile("(?<![A-Za-z0-9_-])([.#][A-Za-z_][A-Za-z0-9_-]*)");
    private static final Pattern SCSS_USE = Pattern.compile("@(use|forward|import)\\s+[\"']([^\"']+)[\"']");
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*[\"']?([^\"')]+)");
    private static final Pattern KEY = Pattern.compile("^\\s*[\"']?([A-Za-z0-9_.-]+)[\"']?\\s*[:=]");

    public String language(String path) {
        if (path == null) return "unknown";
        String name = Path.of(path).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "unknown" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public boolean supported(String language) { return SUPPORTED.contains(language); }

    public List<CrossLanguageObservation> analyze(String path, DiffHunk hunk) {
        String language = language(path);
        List<CrossLanguageObservation> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String line : java.util.stream.Stream.concat(hunk.removedLines().stream(), hunk.addedLines().stream()).toList()) {
            if (language.equals("jelly") || language.equals("xml")) {
                capture(EL, line, "JELLY_PROPERTY_REFERENCE", result, seen);
                capture(ATTR, line, language.equals("jelly") ? "JELLY_ATTRIBUTE_REFERENCE" : "XML_ATTRIBUTE_REFERENCE",
                        result, seen);
                capture(SELECTOR, line, "MARKUP_SELECTOR_REFERENCE", result, seen);
            }
            if (language.equals("scss") || language.equals("css")) {
                capture(SCSS_USE, line, "STYLESHEET_IMPORT", 2, result, seen);
                capture(SELECTOR, line, "CSS_SELECTOR", result, seen);
                capture(CSS_URL, line, "STYLESHEET_RESOURCE_URL", result, seen);
            }
            if (language.equals("svg")) {
                capture(ATTR, line, "SVG_ATTRIBUTE_REFERENCE", result, seen);
            }
            if (language.equals("yaml") || language.equals("yml") || language.equals("json")
                    || language.equals("properties")) {
                capture(KEY, line, "CONFIG_KEY", result, seen);
            }
        }
        if (language.equals("svg")) {
            String file = Path.of(path).getFileName().toString().replaceFirst("\\.svg$", "");
            add("SVG_SYMBOL", file, file, result, seen);
        }
        return List.copyOf(result);
    }

    private void capture(Pattern pattern, String text, String kind, List<CrossLanguageObservation> out,
                         Set<String> seen) { capture(pattern, text, kind, 1, out, seen); }

    private void capture(Pattern pattern, String text, String kind, int group,
                         List<CrossLanguageObservation> out, Set<String> seen) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) add(kind, matcher.group(group), matcher.group(group), out, seen);
    }

    private void add(String kind, String raw, String possible, List<CrossLanguageObservation> out,
                     Set<String> seen) {
        String key = kind + "\u0000" + raw;
        if (seen.add(key)) out.add(new CrossLanguageObservation(kind, raw, possible, false,
                "structural observation only; not inserted into the Java code graph"));
    }
}
