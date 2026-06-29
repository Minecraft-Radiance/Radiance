package com.radiance.loader.neoforge.scene_import.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SceneImportBundleResolver {
    public static final String ACTIVE_BUNDLE_PROPERTY = "radiance.sceneImport.bundleDir";
    public static final String ACTIVE_RULES_PROPERTY = "radiance.sceneImport.rulesPath";
    public static final String DEFAULT_BUNDLE_RESOURCE =
        "/radiance/scene_import/default-scene-import-bundle.zip";
    private static final int DERIVED_FORMAT_VERSION = 2;

    private SceneImportBundleResolver() {
    }

    public static Bundle resolve(Path gameDir) throws IOException {
        Path root = gameDir.resolve("config/radiance/scene_import");
        Files.createDirectories(root);
        Path configuredZip = root.resolve("scene_import_bundle.zip");
        byte[] bundleBytes;
        String source;
        if (Files.isRegularFile(configuredZip)) {
            bundleBytes = Files.readAllBytes(configuredZip);
            source = configuredZip.toAbsolutePath().toString();
        } else {
            try (InputStream in = SceneImportBundleResolver.class.getResourceAsStream(DEFAULT_BUNDLE_RESOURCE)) {
                if (in == null) {
                    throw new IOException("Missing bundled scene import bundle: " + DEFAULT_BUNDLE_RESOURCE);
                }
                bundleBytes = in.readAllBytes();
            }
            source = DEFAULT_BUNDLE_RESOURCE;
        }

        String hash = sha256(bundleBytes);
        Path bundleDir = root.resolve("cache").resolve(hash);
        Path marker = bundleDir.resolve(".radiance-scene-import-bundle");
        if (!isCacheCurrent(bundleDir, marker)) {
            extractZip(bundleBytes, bundleDir);
            Path rules = bundleDir.resolve("rules.yml");
            if (!Files.isRegularFile(rules)) {
                throw new IOException("Scene import bundle has no rules.yml: " + source);
            }
            Files.deleteIfExists(bundleDir.resolve("native_scene_import_rules.json"));
            Files.deleteIfExists(bundleDir.resolve("native_scene_import_kernels.json"));
            generateNativeKernels(bundleDir.resolve("kernels.yml"),
                bundleDir.resolve("native_scene_import_kernels.json"));
            Files.writeString(marker,
                "sha256: " + hash + "\n"
                    + "derivedFormat: " + DERIVED_FORMAT_VERSION + "\n",
                StandardCharsets.UTF_8);
        }

        Path active = root.resolve("active-bundle.yml");
        Files.writeString(active,
            "generatedAt: '" + Instant.now() + "'\n"
                + "source: '" + q(source) + "'\n"
                + "sha256: '" + hash + "'\n"
                + "directory: '" + q(bundleDir.toAbsolutePath().toString()) + "'\n"
                + "rules: '" + q(bundleDir.resolve("rules.yml").toAbsolutePath().toString()) + "'\n",
            StandardCharsets.UTF_8);

        String bundlePath = bundleDir.toAbsolutePath().toString();
        String rulesPath = bundleDir.resolve("rules.yml").toAbsolutePath().toString();
        System.setProperty(ACTIVE_BUNDLE_PROPERTY, bundlePath);
        System.setProperty(ACTIVE_RULES_PROPERTY, rulesPath);
        return new Bundle(bundleDir, bundleDir.resolve("rules.yml"), hash, source);
    }

    private static boolean isCacheCurrent(Path bundleDir, Path marker) throws IOException {
        if (!Files.isRegularFile(marker)
            || !Files.isRegularFile(bundleDir.resolve("rules.yml"))
            || !Files.isRegularFile(bundleDir.resolve("native_scene_import_kernels.json"))) {
            return false;
        }
        String text = Files.readString(marker, StandardCharsets.UTF_8);
        return text.contains("derivedFormat: " + DERIVED_FORMAT_VERSION + "\n");
    }

    private static void extractZip(byte[] bytes, Path dir) throws IOException {
        Files.createDirectories(dir);
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                Path target = dir.resolve(entry.getName()).normalize();
                if (!target.startsWith(dir)) {
                    throw new IOException("Scene import bundle contains unsafe path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void generateNativeKernels(Path kernelsYml, Path output) throws IOException {
        if (!Files.isRegularFile(kernelsYml)) {
            Files.writeString(output, "{\n  \"kernels\": []\n}\n", StandardCharsets.UTF_8);
            return;
        }
        List<Kernel> kernels = parseKernels(Files.readAllLines(kernelsYml, StandardCharsets.UTF_8));
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"kernels\": [\n");
        for (int i = 0; i < kernels.size(); i++) {
            Kernel kernel = kernels.get(i);
            if (i > 0) {
                json.append(",\n");
            }
            json.append("    {\n");
            json.append("      \"id\": \"").append(j(kernel.id)).append("\",\n");
            json.append("      \"format\": \"").append(j(kernel.format)).append("\",\n");
            json.append("      \"name\": \"").append(j(emptyTo(kernel.name, kernel.id))).append("\",\n");
            json.append("      \"shaderPath\": \"").append(j(kernel.shader)).append("\",\n");
            json.append("      \"localSizeX\": ").append(kernel.localSizeX).append(",\n");
            json.append("      \"primitiveStride\": ").append(kernel.primitiveStride).append(",\n");
            json.append("      \"dependencies\": ").append(jsonArray(kernel.dependencies)).append(",\n");
            json.append("      \"geometryDependencies\": ").append(jsonArray(kernel.geometryDependencies)).append("\n");
            json.append("    }");
        }
        json.append("\n  ]\n}\n");
        Files.writeString(output, json.toString(), StandardCharsets.UTF_8);
    }

    private static List<Kernel> parseKernels(List<String> lines) {
        java.util.ArrayList<Kernel> kernels = new java.util.ArrayList<>();
        Kernel current = null;
        for (String raw : lines) {
            String trimmed = stripComment(raw).trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("- id:")) {
                if (current != null && !current.id.isBlank()) {
                    kernels.add(current);
                }
                current = new Kernel();
                current.id = valueAfter(trimmed, "- id:");
                continue;
            }
            if (current == null) {
                continue;
            }
            String key = key(trimmed);
            String value = value(trimmed);
            if (key == null) {
                continue;
            }
            switch (key) {
                case "format" -> current.format = value;
                case "name" -> current.name = value;
                case "shader", "shaderPath" -> current.shader = value;
                case "localSizeX" -> current.localSizeX = number(value, current.localSizeX);
                case "primitiveStride" -> current.primitiveStride = number(value, current.primitiveStride);
                case "dependencies" -> current.dependencies = parseInlineList(value);
                case "geometryDependencies" -> current.geometryDependencies = parseInlineList(value);
                default -> {
                }
            }
        }
        if (current != null && !current.id.isBlank()) {
            kernels.add(current);
        }
        return kernels;
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
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

    private static int number(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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
            .map(SceneImportBundleResolver::unquote)
            .filter(entry -> !entry.isBlank())
            .toList();
    }

    private static String jsonArray(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append('"').append(j(values.get(i))).append('"');
        }
        return out.append(']').toString();
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String q(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static String j(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    public record Bundle(Path directory, Path rulesPath, String sha256, String source) {
    }

    private static final class Kernel {
        String id = "";
        String format = "unknown";
        String name = "";
        String shader = "";
        int localSizeX = 64;
        int primitiveStride = 1;
        List<String> dependencies = List.of();
        List<String> geometryDependencies = List.of();
    }
}
