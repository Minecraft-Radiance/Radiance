package com.radiance.loader.neoforge.scene_import.plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SceneImportCallsiteRules {
    private final List<Rule> rules;

    public SceneImportCallsiteRules(List<Rule> rules) {
        this.rules = rules.stream()
            .sorted(Comparator.comparingInt(Rule::priority).reversed())
            .toList();
    }

    public static SceneImportCallsiteRules load(Path rulesPath) throws IOException {
        return parseLines(Files.readAllLines(rulesPath, StandardCharsets.UTF_8));
    }

    public MatchResult classify(Blaze3DCallsite callsite) {
        for (Rule rule : this.rules) {
            if (rule.matches(callsite)) {
                return new MatchResult(callsite, rule);
            }
        }
        return new MatchResult(callsite, Rule.unknown());
    }

    public List<Rule> rules() {
        return this.rules;
    }

    private static SceneImportCallsiteRules parseLines(List<String> lines) {
        List<Rule> parsed = new ArrayList<>();
        MutableRule current = null;
        boolean inRules = false;
        boolean inMatch = false;
        boolean inInject = false;

        for (String raw : lines) {
            String line = stripComment(raw);
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (!Character.isWhitespace(line.charAt(0)) && trimmed.endsWith(":")) {
                if (current != null && inRules) {
                    parsed.add(current.build());
                }
                current = null;
                inRules = "rules:".equals(trimmed);
                inMatch = false;
                inInject = false;
                continue;
            }
            if (!inRules) {
                continue;
            }
            if (trimmed.startsWith("- id:")) {
                if (current != null) {
                    parsed.add(current.build());
                }
                current = new MutableRule();
                current.id = valueAfter(trimmed, "- id:");
                inMatch = false;
                inInject = false;
                continue;
            }
            if (current == null) {
                continue;
            }
            if ("match:".equals(trimmed)) {
                inMatch = true;
                inInject = false;
                continue;
            }
            if ("inject:".equals(trimmed)) {
                inMatch = false;
                inInject = true;
                continue;
            }
            String key = key(trimmed);
            String value = value(trimmed);
            if (key == null) {
                continue;
            }
            if (inMatch) {
                switch (key) {
                    case "classContains" -> current.classContains = value;
                    case "methodName" -> current.methodName = emptyToNull(value);
                    case "owner" -> current.owner = emptyToNull(value);
                    case "name" -> current.name = emptyToNull(value);
                    case "descriptor" -> current.descriptor = emptyToNull(value);
                    case "category" -> current.category = emptyToNull(value);
                    case "nearbyStringContains" -> current.nearbyStringContains = emptyToNull(value);
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
                    case "transform" -> current.transform = value;
                    case "priority" -> current.priority = number(value, 0);
                    default -> {
                    }
                }
                if (inInject) {
                    switch (key) {
                        case "semantic", "group" -> current.semantic = value;
                        case "sceneRoute" -> current.sceneRoute = value;
                        case "format" -> current.format = value;
                        case "importKernel", "kernelHint" -> current.importKernel = value;
                        case "rasterClass" -> current.rasterClass = value;
                        case "materialPolicy" -> current.materialPolicy = value;
                        case "transform" -> current.transform = value;
                        default -> {
                        }
                    }
                }
            }
        }
        if (current != null) {
            parsed.add(current.build());
        }
        return new SceneImportCallsiteRules(parsed);
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

    public record MatchResult(Blaze3DCallsite callsite, Rule rule) {
        public boolean injectsSceneImport() {
            return this.rule.sceneRouteMask() != SceneImportIds.SCENE_ROUTE_UNSPECIFIED
                || this.rule.formatId() != SceneImportIds.FORMAT_UNKNOWN
                || this.rule.importKernelId() != SceneImportIds.KERNEL_UNKNOWN
                || this.rule.rasterClassId() != SceneImportIds.RASTER_UNKNOWN
                || this.rule.materialPolicyId() != SceneImportIds.MATERIAL_DEFAULT
                || this.rule.transformRecipeId() != SceneImportIds.TRANSFORM_RECIPE_DEFAULT;
        }
    }

    public record Rule(
        String id,
        String semantic,
        String sceneRoute,
        String format,
            String importKernel,
            String rasterClass,
            String materialPolicy,
            String transform,
        int priority,
        Match match,
        int sceneRouteMask,
        int formatId,
        int importKernelId,
        int rasterClassId,
        int materialPolicyId,
        int transformRecipeId) {

        Rule(String id, String semantic, String sceneRoute, String format, String importKernel,
             String rasterClass, String materialPolicy, String transform, int priority, Match match) {
            this(id, semantic, sceneRoute, format, importKernel, rasterClass, materialPolicy,
                transform,
                priority, match,
                SceneImportIds.routeMask(sceneRoute),
                SceneImportIds.formatId(format),
                SceneImportIds.kernelId(importKernel),
                SceneImportIds.rasterClass(rasterClass),
                SceneImportIds.materialPolicy(materialPolicy),
                SceneImportIds.transformRecipeId(transform));
        }

        static Rule unknown() {
            return new Rule("unknown", "unknown", "unspecified", "unknown", "unknown",
                "unknown", "default", "default", 0, Match.empty());
        }

        public boolean injectsSceneImport() {
            return sceneRouteMask != SceneImportIds.SCENE_ROUTE_UNSPECIFIED
                || formatId != SceneImportIds.FORMAT_UNKNOWN
                || importKernelId != SceneImportIds.KERNEL_UNKNOWN
                || rasterClassId != SceneImportIds.RASTER_UNKNOWN
                || materialPolicyId != SceneImportIds.MATERIAL_DEFAULT
                || transformRecipeId != SceneImportIds.TRANSFORM_RECIPE_DEFAULT;
        }

        boolean matches(Blaze3DCallsite callsite) {
            return contains(callsite.className(), this.match.classContains())
                && equalsIfPresent(callsite.methodName(), this.match.methodName())
                && equalsIfPresent(callsite.owner(), this.match.owner())
                && equalsIfPresent(callsite.name(), this.match.name())
                && equalsIfPresent(callsite.descriptor(), this.match.descriptor())
                && equalsIfPresent(callsite.category(), this.match.category())
                && nearbyContains(callsite, this.match.nearbyStringContains());
        }

        private static boolean contains(String value, String needle) {
            return needle == null || needle.isBlank() || value.contains(needle);
        }

        private static boolean equalsIfPresent(String value, String expected) {
            return expected == null || expected.isBlank() || expected.equals(value);
        }

        private static boolean nearbyContains(Blaze3DCallsite callsite, String needle) {
            if (needle == null || needle.isBlank()) {
                return true;
            }
            String lowerNeedle = needle.toLowerCase(Locale.ROOT);
            return callsite.nearbyStrings().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(lowerNeedle));
        }
    }

    public record Match(String classContains, String methodName, String owner, String name,
        String descriptor, String category, String nearbyStringContains) {
        static Match empty() {
            return new Match("", null, null, null, null, null, null);
        }
    }

    private static final class MutableRule {
        String id = "unknown";
        String semantic = "unknown";
        String sceneRoute = "unspecified";
        String format = "unknown";
        String importKernel = "unknown";
        String rasterClass = "unknown";
        String materialPolicy = "default";
        String transform = "default";
        int priority;
        String classContains = "";
        String methodName;
        String owner;
        String name;
        String descriptor;
        String category;
        String nearbyStringContains;

        Rule build() {
            return new Rule(id, semantic, sceneRoute, format, importKernel, rasterClass,
                materialPolicy, transform, priority, new Match(classContains, methodName,
                owner, name, descriptor, category, nearbyStringContains));
        }
    }
}

