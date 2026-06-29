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

public final class SceneImportCallsiteInjectionReport {
    private static final Map<String, CallsiteInjectionRecord> RECORDS = new LinkedHashMap<>();
    private static Path runDir;
    private static Path gameDir;
    private static boolean shutdownHookRegistered;
    private static boolean flushScheduled;
    private static ScheduledExecutorService executor;

    private SceneImportCallsiteInjectionReport() {
    }

    public static void initialize(Path gameDir) {
        synchronized (SceneImportCallsiteInjectionReport.class) {
            SceneImportCallsiteInjectionReport.gameDir = gameDir;
        }
        ensureRunDir(gameDir);
        registerShutdownHook(gameDir);
    }

    public static void record(CallsiteInjectionRecord record) {
        synchronized (RECORDS) {
            RECORDS.put(recordKey(record), record);
        }
        scheduleWrite();
    }

    public static void write(Path gameDir) {
        List<CallsiteInjectionRecord> snapshot;
        synchronized (RECORDS) {
            snapshot = List.copyOf(RECORDS.values());
        }
        Path outputDir = ensureRunDir(gameDir).resolve("static");
        try {
            writeSummary(outputDir.resolve("callsite-injection-summary.yml"), snapshot);
            writeGroups(outputDir.resolve("semantic"), "semantic", snapshot,
                CallsiteInjectionRecord::semantic);
            writeGroups(outputDir.resolve("format"), "format", snapshot,
                CallsiteInjectionRecord::format);
        } catch (IOException ignored) {
            // Diagnostics must never break client startup.
        }
    }

    private static Path ensureRunDir(Path gameDir) {
        synchronized (SceneImportCallsiteInjectionReport.class) {
            if (runDir == null) {
                Path root = gameDir.resolve("radiance-scene-import");
                runDir = root.resolve("run-" + Instant.now().toString().replace(':', '-'));
            }
            return runDir;
        }
    }

    private static void registerShutdownHook(Path gameDir) {
        synchronized (SceneImportCallsiteInjectionReport.class) {
            if (shutdownHookRegistered) {
                return;
            }
            shutdownHookRegistered = true;
            Runtime.getRuntime().addShutdownHook(new Thread(
                () -> write(gameDir),
                "Radiance scene import callsite report"));
        }
    }

    private static void scheduleWrite() {
        Path outputGameDir;
        synchronized (SceneImportCallsiteInjectionReport.class) {
            outputGameDir = gameDir;
            if (outputGameDir == null || flushScheduled) {
                return;
            }
            flushScheduled = true;
            if (executor == null) {
                executor = Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "Radiance scene import callsite report writer");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            executor.schedule(() -> {
                try {
                    write(outputGameDir);
                } finally {
                    synchronized (SceneImportCallsiteInjectionReport.class) {
                        flushScheduled = false;
                    }
                }
            }, 3, TimeUnit.SECONDS);
        }
    }

    private static void writeSummary(Path path, List<CallsiteInjectionRecord> records)
        throws IOException {
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("generatedAt: ");
            writer.write(q(Instant.now().toString()));
            writer.newLine();
            writer.write("count: " + records.size());
            writer.newLine();
            writer.write("injectedCount: " + records.stream().filter(CallsiteInjectionRecord::injected).count());
            writer.newLine();
            writeCounts(writer, "categories", records, CallsiteInjectionRecord::category, 0);
            writeCounts(writer, "rules", records, CallsiteInjectionRecord::ruleId, 0);
            writeCounts(writer, "semantics", records, CallsiteInjectionRecord::semantic, 0);
            writeCounts(writer, "formats", records, CallsiteInjectionRecord::format, 0);
            writer.write("callsites:");
            writer.newLine();
            for (CallsiteInjectionRecord record : sorted(records)) {
                writer.write("  - className: ");
                writer.write(q(record.className()));
                writer.newLine();
                writer.write("    methodName: ");
                writer.write(q(record.methodName()));
                writer.newLine();
                writer.write("    line: " + record.line());
                writer.newLine();
                writer.write("    call: ");
                writer.write(q(record.owner() + "." + record.name() + record.descriptor()));
                writer.newLine();
                writer.write("    category: ");
                writer.write(q(record.category()));
                writer.newLine();
                writer.write("    nearbyStrings: ");
                writer.write(inlineList(record.nearbyStrings()));
                writer.newLine();
                writer.write("    rule: ");
                writer.write(q(record.ruleId()));
                writer.newLine();
                writer.write("    injected: " + record.injected());
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

    private static void writeGroups(Path dir, String key, List<CallsiteInjectionRecord> records,
        KeyFn groupFn) throws IOException {
        Files.createDirectories(dir);
        Map<String, List<CallsiteInjectionRecord>> groups = new LinkedHashMap<>();
        for (CallsiteInjectionRecord record : sorted(records)) {
            groups.computeIfAbsent(groupFn.key(record), ignored -> new ArrayList<>()).add(record);
        }
        for (Map.Entry<String, List<CallsiteInjectionRecord>> entry : groups.entrySet()) {
            Path path = dir.resolve(safeFileName(entry.getKey()) + ".yml");
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write(key);
                writer.write(": ");
                writer.write(q(entry.getKey()));
                writer.newLine();
                writer.write("count: " + entry.getValue().size());
                writer.newLine();
                writer.write("injectedCount: "
                    + entry.getValue().stream().filter(CallsiteInjectionRecord::injected).count());
                writer.newLine();
                writeCounts(writer, "rules", entry.getValue(), CallsiteInjectionRecord::ruleId, 0);
                writeCounts(writer, "classes", entry.getValue(), CallsiteInjectionRecord::className, 0);
                writeCounts(writer, "categories", entry.getValue(), CallsiteInjectionRecord::category, 0);
                writer.write("callsites:");
                writer.newLine();
                for (CallsiteInjectionRecord record : entry.getValue()) {
                    writer.write("  - className: ");
                    writer.write(q(record.className()));
                    writer.newLine();
                    writer.write("    methodName: ");
                    writer.write(q(record.methodName()));
                    writer.newLine();
                    writer.write("    line: " + record.line());
                    writer.newLine();
                    writer.write("    call: ");
                    writer.write(q(record.owner() + "." + record.name() + record.descriptor()));
                    writer.newLine();
                    writer.write("    rule: ");
                    writer.write(q(record.ruleId()));
                    writer.newLine();
                    writer.write("    injected: " + record.injected());
                    writer.newLine();
                }
            }
        }
    }

    private static List<CallsiteInjectionRecord> sorted(List<CallsiteInjectionRecord> records) {
        return records.stream()
            .sorted(Comparator.comparing(CallsiteInjectionRecord::className)
                .thenComparing(CallsiteInjectionRecord::methodName)
                .thenComparingInt(CallsiteInjectionRecord::line)
                .thenComparing(CallsiteInjectionRecord::name))
            .toList();
    }

    private static void writeCounts(BufferedWriter writer, String key,
        List<CallsiteInjectionRecord> records, KeyFn keyFn, int indent) throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CallsiteInjectionRecord record : records) {
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

    private static String inlineList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(q(values.get(i)));
        }
        return out.append(']').toString();
    }

    private static String recordKey(CallsiteInjectionRecord record) {
        return record.className() + '\n'
            + record.methodName() + '\n'
            + record.methodDescriptor() + '\n'
            + record.line() + '\n'
            + record.owner() + '\n'
            + record.name() + '\n'
            + record.descriptor();
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private interface KeyFn {
        String key(CallsiteInjectionRecord record);
    }
}

