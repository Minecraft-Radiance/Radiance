package com.radiance.loader.neoforge.scene_import.plugin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SceneImportPipelineClassificationReport {
    private static final Map<String, PipelineRecord> RECORDS = new LinkedHashMap<>();
    private static Path gameDir;
    private static Path runDir;
    private static boolean shutdownHookRegistered;
    private static boolean flushScheduled;
    private static ScheduledExecutorService executor;

    private SceneImportPipelineClassificationReport() {
    }

    public static void initialize(Path gameDir) {
        synchronized (SceneImportPipelineClassificationReport.class) {
            SceneImportPipelineClassificationReport.gameDir = gameDir;
        }
        ensureRunDir(gameDir);
        registerShutdownHook(gameDir);
    }

    public static void record(SceneImportPipelineRules.MatchResult result) {
        SceneImportPipelineRules.PipelineInfo pipeline = result.pipeline();
        SceneImportPipelineRules.Rule rule = result.rule();
        PipelineRecord record = new PipelineRecord(
            pipeline.location(),
            pipeline.topology(),
            pipeline.slot(),
            pipeline.stride(),
            pipeline.attributes(),
            pipeline.hasBlend(),
            pipeline.blendName(),
            pipeline.hasDepth(),
            pipeline.depthWrite(),
            pipeline.cull(),
            rule.id(),
            rule.semantic(),
            rule.sceneRoute(),
            rule.format(),
            rule.importKernel(),
            rule.rasterClass(),
            rule.materialPolicy(),
            rule.transformPolicy(),
            rule.sceneRouteMask(),
            rule.formatId(),
            rule.importKernelId(),
            rule.rasterClassId(),
            rule.materialPolicyId(),
            rule.transformPolicyId());
        synchronized (RECORDS) {
            RECORDS.put(recordKey(record), record);
        }
        scheduleWrite();
    }

    public static void write(Path gameDir) {
        List<PipelineRecord> snapshot;
        synchronized (RECORDS) {
            snapshot = List.copyOf(RECORDS.values());
        }
        Path outputDir = ensureRunDir(gameDir).resolve("pipelines");
        try {
            writeSummary(outputDir.resolve("pipeline-classification-summary.yml"), snapshot);
            writeGroups(outputDir.resolve("semantic"), "semantic", snapshot, PipelineRecord::semantic);
            writeGroups(outputDir.resolve("format"), "format", snapshot, PipelineRecord::format);
        } catch (IOException ignored) {
            // Diagnostics must never break client startup.
        }
    }

    private static Path ensureRunDir(Path gameDir) {
        synchronized (SceneImportPipelineClassificationReport.class) {
            if (runDir == null) {
                Path root = gameDir.resolve("radiance-scene-import");
                runDir = root.resolve("run-" + Instant.now().toString().replace(':', '-'));
            }
            return runDir;
        }
    }

    private static void registerShutdownHook(Path gameDir) {
        synchronized (SceneImportPipelineClassificationReport.class) {
            if (shutdownHookRegistered) {
                return;
            }
            shutdownHookRegistered = true;
            Runtime.getRuntime().addShutdownHook(new Thread(
                () -> write(gameDir),
                "Radiance scene import pipeline report"));
        }
    }

    private static void scheduleWrite() {
        Path outputGameDir;
        synchronized (SceneImportPipelineClassificationReport.class) {
            outputGameDir = gameDir;
            if (outputGameDir == null || flushScheduled) {
                return;
            }
            flushScheduled = true;
            if (executor == null) {
                executor = Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "Radiance scene import pipeline report writer");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            executor.schedule(() -> {
                try {
                    write(outputGameDir);
                } finally {
                    synchronized (SceneImportPipelineClassificationReport.class) {
                        flushScheduled = false;
                    }
                }
            }, 3, TimeUnit.SECONDS);
        }
    }

    private static void writeSummary(Path path, List<PipelineRecord> records) throws IOException {
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("generatedAt: ");
            writer.write(q(Instant.now().toString()));
            writer.newLine();
            writer.write("count: " + records.size());
            writer.newLine();
            writeCounts(writer, "rules", records, PipelineRecord::ruleId, 0);
            writeCounts(writer, "semantics", records, PipelineRecord::semantic, 0);
            writeCounts(writer, "formats", records, PipelineRecord::format, 0);
            writer.write("pipelines:");
            writer.newLine();
            for (PipelineRecord record : sorted(records)) {
                writer.write("  - location: ");
                writer.write(q(record.location()));
                writer.newLine();
                writer.write("    topology: ");
                writer.write(q(record.topology()));
                writer.newLine();
                writer.write("    slot: " + record.slot());
                writer.newLine();
                writer.write("    stride: " + record.stride());
                writer.newLine();
                writer.write("    attributes: ");
                writer.write(attributes(record.attributes()));
                writer.newLine();
                writer.write("    pipelineState: { hasBlend: " + record.hasBlend()
                    + ", blendName: " + q(record.blendName())
                    + ", hasDepth: " + record.hasDepth()
                    + ", depthWrite: " + record.depthWrite()
                    + ", cull: " + record.cull() + " }");
                writer.newLine();
                writer.write("    rule: ");
                writer.write(q(record.ruleId()));
                writer.newLine();
                writer.write("    classification:");
                writer.newLine();
                writer.write("      semantic: ");
                writer.write(q(record.semantic()));
                writer.newLine();
                writer.write("      sceneRoute: ");
                writer.write(q(record.sceneRoute()));
                writer.newLine();
                writer.write("      format: ");
                writer.write(q(record.format()));
                writer.newLine();
                writer.write("      importKernel: ");
                writer.write(q(record.importKernel()));
                writer.newLine();
                writer.write("      rasterClass: ");
                writer.write(q(record.rasterClass()));
                writer.newLine();
                writer.write("      materialPolicy: ");
                writer.write(q(record.materialPolicy()));
                writer.newLine();
                writer.write("      transformPolicy: ");
                writer.write(q(record.transformPolicy()));
                writer.newLine();
                writer.write("      numeric: { sceneRouteMask: " + record.sceneRouteMask()
                    + ", formatId: " + record.formatId()
                    + ", importKernelId: " + record.importKernelId()
                    + ", rasterClass: " + record.rasterClassId()
                    + ", materialPolicy: " + record.materialPolicyId()
                    + ", transformPolicy: " + record.transformPolicyId() + " }");
                writer.newLine();
            }
        }
    }

    private static void writeGroups(Path dir, String key, List<PipelineRecord> records,
        KeyFn groupFn) throws IOException {
        Files.createDirectories(dir);
        Map<String, List<PipelineRecord>> groups = new LinkedHashMap<>();
        for (PipelineRecord record : sorted(records)) {
            groups.computeIfAbsent(groupFn.key(record), ignored -> new ArrayList<>()).add(record);
        }
        for (Map.Entry<String, List<PipelineRecord>> entry : groups.entrySet()) {
            Path path = dir.resolve(safeFileName(entry.getKey()) + ".yml");
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write(key);
                writer.write(": ");
                writer.write(q(entry.getKey()));
                writer.newLine();
                writer.write("count: " + entry.getValue().size());
                writer.newLine();
                writeCounts(writer, "rules", entry.getValue(), PipelineRecord::ruleId, 0);
                writer.write("pipelines:");
                writer.newLine();
                for (PipelineRecord record : entry.getValue()) {
                    writer.write("  - location: ");
                    writer.write(q(record.location()));
                    writer.newLine();
                    writer.write("    rule: ");
                    writer.write(q(record.ruleId()));
                    writer.newLine();
                    writer.write("    format: ");
                    writer.write(q(record.format()));
                    writer.newLine();
                    writer.write("    importKernel: ");
                    writer.write(q(record.importKernel()));
                    writer.newLine();
                }
            }
        }
    }

    private static List<PipelineRecord> sorted(List<PipelineRecord> records) {
        return records.stream()
            .sorted(Comparator.comparing(PipelineRecord::location)
                .thenComparing(PipelineRecord::topology)
                .thenComparingInt(PipelineRecord::stride))
            .toList();
    }

    private static void writeCounts(BufferedWriter writer, String key, List<PipelineRecord> records,
        KeyFn keyFn, int indent) throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PipelineRecord record : records) {
            counts.merge(keyFn.key(record), 1, Integer::sum);
        }
        String prefix = " ".repeat(indent);
        writer.write(prefix);
        writer.write(key);
        writer.write(":");
        writer.newLine();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            writer.write(prefix);
            writer.write("  - name: ");
            writer.write(q(entry.getKey()));
            writer.newLine();
            writer.write(prefix);
            writer.write("    count: " + entry.getValue());
            writer.newLine();
        }
    }

    private static String q(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "''") + "'";
    }

    private static String attributes(List<SceneImportPipelineRules.Attribute> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < attributes.size(); i++) {
            SceneImportPipelineRules.Attribute attribute = attributes.get(i);
            if (i > 0) {
                out.append(", ");
            }
            out.append(q(attribute.name() + "@" + attribute.offset() + ":" + attribute.format()));
        }
        return out.append(']').toString();
    }

    private static String recordKey(PipelineRecord record) {
        return record.location() + '\n' + record.topology() + '\n' + record.slot() + '\n'
            + record.stride() + '\n' + attributes(record.attributes());
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public record PipelineRecord(
        String location,
        String topology,
        int slot,
        int stride,
        List<SceneImportPipelineRules.Attribute> attributes,
        boolean hasBlend,
        String blendName,
        boolean hasDepth,
        boolean depthWrite,
        boolean cull,
        String ruleId,
        String semantic,
        String sceneRoute,
        String format,
        String importKernel,
        String rasterClass,
        String materialPolicy,
        String transformPolicy,
        int sceneRouteMask,
        int formatId,
        int importKernelId,
        int rasterClassId,
        int materialPolicyId,
        int transformPolicyId) {
    }

    private interface KeyFn {
        String key(PipelineRecord record);
    }
}
