package com.radiance.loader.neoforge.scene_import.plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SceneImportPipelineRules {
    private static volatile SceneImportPipelineRules activeRules;
    private static volatile String activeRulesPath = "";

    private final List<Rule> rules;

    public SceneImportPipelineRules(List<Rule> rules) {
        this.rules = rules.stream()
            .sorted(Comparator.comparingInt(Rule::priority).reversed())
            .toList();
    }

    public static SceneImportPipelineRules load(Path rulesPath) throws IOException {
        return parseLines(Files.readAllLines(rulesPath, StandardCharsets.UTF_8));
    }

    public static SceneImportPipelineRules active() {
        String path = System.getProperty(SceneImportBundleResolver.ACTIVE_RULES_PROPERTY, "");
        SceneImportPipelineRules cached = activeRules;
        if (cached != null && path.equals(activeRulesPath)) {
            return cached;
        }
        synchronized (SceneImportPipelineRules.class) {
            cached = activeRules;
            if (cached != null && path.equals(activeRulesPath)) {
                return cached;
            }
            SceneImportPipelineRules loaded = new SceneImportPipelineRules(List.of());
            if (!path.isBlank()) {
                try {
                    loaded = load(Path.of(path));
                } catch (IOException ignored) {
                    loaded = new SceneImportPipelineRules(List.of());
                }
            }
            activeRulesPath = path;
            activeRules = loaded;
            return loaded;
        }
    }

    public MatchResult classify(PipelineInfo pipeline) {
        for (Rule rule : this.rules) {
            if (rule.matches(pipeline)) {
                return new MatchResult(pipeline, rule);
            }
        }
        return new MatchResult(pipeline, Rule.unknown());
    }

    public List<Rule> rules() {
        return this.rules;
    }

    private static SceneImportPipelineRules parseLines(List<String> lines) {
        List<Rule> parsed = new ArrayList<>();
        MutableRule current = null;
        boolean inPipelineRules = false;
        boolean inMatch = false;

        for (String raw : lines) {
            String line = stripComment(raw);
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (!Character.isWhitespace(line.charAt(0)) && trimmed.endsWith(":")) {
                if (current != null && inPipelineRules) {
                    parsed.add(current.build());
                }
                current = null;
                inPipelineRules = "pipelineRules:".equals(trimmed);
                inMatch = false;
                continue;
            }
            if (!inPipelineRules) {
                continue;
            }
            if (trimmed.startsWith("- id:")) {
                if (current != null) {
                    parsed.add(current.build());
                }
                current = new MutableRule();
                current.id = valueAfter(trimmed, "- id:");
                inMatch = false;
                continue;
            }
            if (current == null) {
                continue;
            }
            if ("match:".equals(trimmed)) {
                inMatch = true;
                continue;
            }
            String key = key(trimmed);
            String value = value(trimmed);
            if (key == null) {
                continue;
            }
            if (inMatch) {
                switch (key) {
                    case "location" -> current.locations = List.of(value);
                    case "locations" -> current.locations = parseInlineList(value);
                    case "locationContains" -> current.locationContains = emptyToNull(value);
                    case "topology" -> current.topology = emptyToNull(value);
                    case "slot" -> current.slot = number(value, current.slot);
                    case "stride" -> current.stride = number(value, current.stride);
                    case "attributes" -> current.attributes = parseAttributes(value);
                    case "hasBlend" -> current.hasBlend = boolOrNull(value);
                    case "blendNameContains" -> current.blendNameContains = emptyToNull(value);
                    case "hasDepth" -> current.hasDepth = boolOrNull(value);
                    case "depthWrite" -> current.depthWrite = boolOrNull(value);
                    case "cull" -> current.cull = boolOrNull(value);
                    default -> {
                    }
                }
            } else {
                switch (key) {
                    case "semantic", "group" -> current.semantic = value;
                    case "sceneRoute" -> current.sceneRoute = value;
                    case "format" -> current.format = value;
                    case "importKernel", "kernelHint" -> current.importKernel = value;
                    case "rasterClass" -> current.rasterClass = value;
                    case "materialPolicy" -> current.materialPolicy = value;
                    case "transformPolicy" -> current.transformPolicy = value;
                    case "priority" -> current.priority = number(value, 0);
                    default -> {
                    }
                }
            }
        }
        if (current != null && inPipelineRules) {
            parsed.add(current.build());
        }
        return new SceneImportPipelineRules(parsed);
    }

    private static String stripComment(String raw) {
        int index = raw.indexOf('#');
        return index >= 0 ? raw.substring(0, index) : raw;
    }

    private static String key(String trimmed) {
        int index = trimmed.indexOf(':');
        return index >= 0 ? trimmed.substring(0, index).trim() : null;
    }

    private static String value(String trimmed) {
        int index = trimmed.indexOf(':');
        return index >= 0 ? unquote(trimmed.substring(index + 1).trim()) : "";
    }

    private static String valueAfter(String trimmed, String prefix) {
        return unquote(trimmed.substring(prefix.length()).trim());
    }

    private static String unquote(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int number(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Boolean boolOrNull(String value) {
        String text = normalize(value);
        if ("true".equals(text) || "yes".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equals(text) || "no".equals(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static List<String> parseInlineList(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(trimmed.split(","))
            .map(String::trim)
            .map(SceneImportPipelineRules::unquote)
            .filter(entry -> !entry.isBlank())
            .toList();
    }

    private static List<AttributeMatch> parseAttributes(String value) {
        List<AttributeMatch> attributes = new ArrayList<>();
        for (String entry : parseInlineList(value)) {
            String[] nameAndRest = entry.split("@", 2);
            if (nameAndRest.length != 2) {
                continue;
            }
            String[] offsetAndFormat = nameAndRest[1].split(":", 2);
            if (offsetAndFormat.length != 2) {
                continue;
            }
            attributes.add(new AttributeMatch(
                nameAndRest[0].trim(),
                number(offsetAndFormat[0].trim(), -1),
                offsetAndFormat[1].trim()));
        }
        return attributes;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace('-', '_').toLowerCase(Locale.ROOT);
    }

    public record MatchResult(PipelineInfo pipeline, Rule rule) {
    }

    public record Rule(
        String id,
        String semantic,
        String sceneRoute,
        String format,
        String importKernel,
        String rasterClass,
        String materialPolicy,
        String transformPolicy,
        int priority,
        Match match,
        int sceneRouteMask,
        int formatId,
        int importKernelId,
        int rasterClassId,
        int materialPolicyId,
        int transformPolicyId) {

        Rule(String id, String semantic, String sceneRoute, String format, String importKernel,
             String rasterClass, String materialPolicy, String transformPolicy, int priority, Match match) {
            this(id, semantic, sceneRoute, format, importKernel, rasterClass, materialPolicy,
                transformPolicy,
                priority, match,
                SceneImportIds.routeMask(sceneRoute),
                SceneImportIds.formatId(format),
                SceneImportIds.kernelId(importKernel),
                SceneImportIds.rasterClass(rasterClass),
                SceneImportIds.materialPolicy(materialPolicy),
                SceneImportIds.transformPolicy(transformPolicy));
        }

        static Rule unknown() {
            return new Rule("unknown", "unknown", "unspecified", "unknown", "unknown",
                "unknown", "default", "default", 0, Match.empty());
        }

        boolean matches(PipelineInfo pipeline) {
            return locationMatches(pipeline.location(), this.match.locations(), this.match.locationContains())
                && equalsIfPresent(normalize(pipeline.topology()), normalize(this.match.topology()))
                && intEqualsIfPresent(pipeline.slot(), this.match.slot())
                && intEqualsIfPresent(pipeline.stride(), this.match.stride())
                && attributesMatch(pipeline.attributes(), this.match.attributes())
                && boolEqualsIfPresent(pipeline.hasBlend(), this.match.hasBlend())
                && containsIfPresent(normalize(pipeline.blendName()), normalize(this.match.blendNameContains()))
                && boolEqualsIfPresent(pipeline.hasDepth(), this.match.hasDepth())
                && boolEqualsIfPresent(pipeline.depthWrite(), this.match.depthWrite())
                && boolEqualsIfPresent(pipeline.cull(), this.match.cull());
        }

        private static boolean locationMatches(String location, List<String> locations,
                                               String contains) {
            if (locations != null && !locations.isEmpty()) {
                boolean matched = false;
                for (String candidate : locations) {
                    if (candidate.equals(location)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            }
            return containsIfPresent(location, contains);
        }

        private static boolean equalsIfPresent(String value, String expected) {
            return expected == null || expected.isBlank() || expected.equals(value);
        }

        private static boolean containsIfPresent(String value, String needle) {
            return needle == null || needle.isBlank() || value.contains(needle);
        }

        private static boolean intEqualsIfPresent(int value, int expected) {
            return expected < 0 || expected == value;
        }

        private static boolean boolEqualsIfPresent(boolean value, Boolean expected) {
            return expected == null || expected == value;
        }

        private static boolean attributesMatch(List<Attribute> actual, List<AttributeMatch> expected) {
            if (expected == null || expected.isEmpty()) {
                return true;
            }
            if (actual.size() != expected.size()) {
                return false;
            }
            for (int i = 0; i < expected.size(); i++) {
                Attribute a = actual.get(i);
                AttributeMatch e = expected.get(i);
                if (!e.name().equals(a.name())
                    || e.offset() != a.offset()
                    || !normalize(e.format()).equals(normalize(a.format()))) {
                    return false;
                }
            }
            return true;
        }
    }

    public record Match(
        List<String> locations,
        String locationContains,
        String topology,
        int slot,
        int stride,
        List<AttributeMatch> attributes,
        Boolean hasBlend,
        String blendNameContains,
        Boolean hasDepth,
        Boolean depthWrite,
        Boolean cull) {
        static Match empty() {
            return new Match(List.of(), null, null, -1, -1, List.of(), null, null,
                null, null, null);
        }
    }

    public record PipelineInfo(
        String location,
        String topology,
        int slot,
        int stride,
        List<Attribute> attributes,
        boolean hasBlend,
        String blendName,
        boolean hasDepth,
        boolean depthWrite,
        boolean cull) {
    }

    public record Attribute(String name, int offset, String format) {
    }

    public record AttributeMatch(String name, int offset, String format) {
    }

    private static final class MutableRule {
        String id = "unknown";
        String semantic = "unknown";
        String sceneRoute = "unspecified";
        String format = "unknown";
        String importKernel = "unknown";
        String rasterClass = "unknown";
        String materialPolicy = "default";
        String transformPolicy = "default";
        int priority;
        List<String> locations = List.of();
        String locationContains;
        String topology;
        int slot = -1;
        int stride = -1;
        List<AttributeMatch> attributes = List.of();
        Boolean hasBlend;
        String blendNameContains;
        Boolean hasDepth;
        Boolean depthWrite;
        Boolean cull;

        Rule build() {
            return new Rule(id, semantic, sceneRoute, format, importKernel, rasterClass,
                materialPolicy, transformPolicy, priority, new Match(locations,
                locationContains, topology, slot, stride, attributes, hasBlend,
                blendNameContains, hasDepth, depthWrite, cull));
        }
    }
}
