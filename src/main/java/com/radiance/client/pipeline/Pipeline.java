package com.radiance.client.pipeline;

import com.radiance.client.RadianceClient;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.config.AttributeConfig;
import com.radiance.client.pipeline.config.ImageConfig;
import com.radiance.client.profiler.Profiler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryUtil;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.inspector.TagInspector;

public class Pipeline {

    public static Pipeline INSTANCE = new Pipeline();
    private static boolean sharedEntityGeometrySupported;
    private static final String MAIN_RENDER_MODULE_NAME = "render_pipeline.module.main_render.name";
    private static final String MAIN_RENDER_SHADER_PACK_PATH_ATTRIBUTE = "render_pipeline.module.main_render.attribute.shader_pack_path";
    private static final String ADVANCED_RAY_TRACING_SHADER_PACK_PATH = "shaders/world/ray_tracing/advanced.zip";
    private static final String VANILLA_RAY_TRACING_SHADER_PACK_PATH = "shaders/world/ray_tracing/vanilla-pt.zip";
    private static final String DEFAULT_RAY_TRACING_SHADER_PACK_PATH = ADVANCED_RAY_TRACING_SHADER_PACK_PATH;
    private static final String EMISSION_FALLBACK_RAY_TRACING_SHADER_PACK_PATH = VANILLA_RAY_TRACING_SHADER_PACK_PATH;
    private static final String INTERNAL_RAY_TRACING_SHADER_PACK_PATH = DEFAULT_RAY_TRACING_SHADER_PACK_PATH;
    private static final String INTERNAL_SHADER_PACK_DIRECTORY = "shaders/world/ray_tracing";
    private static final String MINECRAFT_SHADER_PACK_DIRECTORY = "shaderpacks";
    private static final String SHADER_PACK_SETTINGS_DIRECTORY = "shader-pack-settings";
    private static final String SHADER_PACK_SETTINGS_EXTENSION = ".properties";
    private static final String LEGACY_SHADER_PACK_SETTINGS_SUFFIX = ".txt";
    private static final String SHADER_PACK_CONFIG_FILE = "configs.json";
    private static final String SHADER_PACK_MANIFEST_KEY = "radiance";
    private static final String SHADER_PACK_MANIFEST_TYPE_KEY = "shader_pack";
    private static final String SHADER_PACK_MANIFEST_DISPLAY_NAME_KEY = "display_name";
    private static final String DLSS_RR_MODULE_NAME = "render_pipeline.module.dlss_rr.name";
    private static final String DLSS_SR_MODULE_NAME = "render_pipeline.module.dlss_sr.name";
    private static final String NRD_MODULE_NAME = "render_pipeline.module.nrd.name";
    private static final String TEMPORAL_ACCUMULATION_MODULE_NAME = "render_pipeline.module.temporal_accumulation.name";
    private static final String FSR3_MODULE_NAME = "render_pipeline.module.fsr_upscaler.name";
    private static final String XESS_MODULE_NAME = "render_pipeline.module.xess_sr.name";
    private static final String TONE_MAPPING_MODULE_NAME = "render_pipeline.module.tone_mapping.name";
    private static final String POST_RENDER_MODULE_NAME = "render_pipeline.module.post_render.name";
    private static final Map<String, String> LEGACY_MAIN_RENDER_OUTPUT_IMAGE_NAMES = Map.ofEntries(
            Map.entry("specular_hit_depth", "indirect_specular_hit_distance"),
            Map.entry("diffuse_hit_distance", "indirect_diffuse_hit_distance"),
            Map.entry("first_hit_diffuse", "indirect_diffuse_radiance"),
            Map.entry("first_hit_diffuse_direction", "indirect_diffuse_direction"),
            Map.entry("first_hit_specular", "indirect_specular_radiance"),
            Map.entry("first_hit_specular_direction", "indirect_specular_direction"),
            Map.entry("direct_diffuse", "direct_diffuse_radiance"),
            Map.entry("direct_specular", "direct_specular_radiance"),
            Map.entry("direct_diffuse_hit_distance", "direct_hit_distance"),
            Map.entry("sun_unshadowed_diffuse", "direct_light_guide_normal_roughness"),
            Map.entry("sun_unshadowed_specular", "direct_light_guide_motion_depth"));
    private static final Map<String, String> LEGACY_NRD_INPUT_IMAGE_NAMES = Map.ofEntries(
            Map.entry("diffuse_radiance", "indirect_diffuse_radiance"),
            Map.entry("diffuse_direction", "indirect_diffuse_direction"),
            Map.entry("specular_radiance", "indirect_specular_radiance"),
            Map.entry("specular_direction", "indirect_specular_direction"),
            Map.entry("diffuse_albedo", "shared_diffuse_albedo_metallic"),
            Map.entry("specular_albedo", "shared_specular_albedo"),
            Map.entry("normal_roughness", "indirect_guide_normal_roughness"),
            Map.entry("motion_vector", "indirect_guide_motion"),
            Map.entry("linear_depth", "indirect_guide_depth"),
            Map.entry("first_hit_clear", "compose_clear_radiance"),
            Map.entry("first_hit_base_emission", "compose_base_emission"),
            Map.entry("fog_image", "compose_fog"),
            Map.entry("diffuse_hit_distance", "indirect_diffuse_hit_distance"),
            Map.entry("specularHitDepthImage", "indirect_specular_hit_distance"),
            Map.entry("direct_diffuse", "direct_diffuse_radiance"),
            Map.entry("direct_specular", "direct_specular_radiance"),
            Map.entry("direct_diffuse_hit_distance", "direct_hit_distance"),
            Map.entry("sun_unshadowed_diffuse", "direct_light_guide_normal_roughness"),
            Map.entry("sun_unshadowed_specular", "direct_light_guide_motion_depth"));
    private static Path PIPELINE_CONFIG_PATH = null;
    private final List<Module> modules = new ArrayList<>();
    private final Map<ImageConfig, List<ImageConfig>> moduleConnections = new HashMap<>();
    private Map<String, ModuleEntry> moduleEntries;

    private PipelineMode mode = PipelineMode.PRESET;
    private String activePresetName = null;

    private Pipeline() {
    }

    public record ShaderPackChoice(String id, String displayName, String relativePath) {
    }

    public static void initFolderPath(Path folderPath) {
        PIPELINE_CONFIG_PATH = folderPath.resolve("pipeline.yaml");
    }

    public static void reloadAllModuleEntries() {
        try {
            INSTANCE.moduleEntries = ModuleEntry.loadAllModuleEntries();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void clear() {
        INSTANCE.modules.clear();
        INSTANCE.moduleConnections.clear();
    }

    public static Module addModule(String name) {
        if (!isModuleAvailable(name)) {
            throw new RuntimeException("Module with name " + name + " is not available.");
        }

        ModuleEntry moduleEntry = INSTANCE.moduleEntries.get(name);
        if (moduleEntry == null) {
            throw new RuntimeException("Module with name " + name + " not found.");
        }

        Module module = moduleEntry.loadModule();
        if (module == null) {
            throw new RuntimeException("Module with name " + name + " not found.");
        }
        getModuleAttributes(module);
        INSTANCE.modules.add(module);

        return module;
    }

    public static boolean isModuleAvailable(String moduleName) {
        if (moduleName == null || moduleName.isEmpty()) {
            return false;
        }
        if (INSTANCE.moduleEntries == null || !INSTANCE.moduleEntries.containsKey(moduleName)) {
            return false;
        }
        return isNativeModuleAvailable(moduleName);
    }

    private static boolean areModulesAvailable(String... moduleNames) {
        if (moduleNames == null || moduleNames.length == 0) {
            return false;
        }
        for (String moduleName : moduleNames) {
            if (!isModuleAvailable(moduleName)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isPresetAvailable(String presetName) {
        if (Objects.equals(presetName, Presets.RT_DLSSRR.key)) {
            return areModulesAvailable(
                    MAIN_RENDER_MODULE_NAME,
                    DLSS_RR_MODULE_NAME,
                    TONE_MAPPING_MODULE_NAME,
                    POST_RENDER_MODULE_NAME);
        }

        if (Objects.equals(presetName, Presets.RT_NRD_DLSS.key)) {
            return areModulesAvailable(
                    MAIN_RENDER_MODULE_NAME,
                    NRD_MODULE_NAME,
                    DLSS_SR_MODULE_NAME,
                    TONE_MAPPING_MODULE_NAME,
                    POST_RENDER_MODULE_NAME);
        }

        if (Objects.equals(presetName, Presets.RT_NRD_FSR.key)) {
            return areModulesAvailable(
                    MAIN_RENDER_MODULE_NAME,
                    NRD_MODULE_NAME,
                    FSR3_MODULE_NAME,
                    TONE_MAPPING_MODULE_NAME,
                    POST_RENDER_MODULE_NAME);
        }

        if (Objects.equals(presetName, Presets.RT_NRD_XESS.key)) {
            return areModulesAvailable(
                    MAIN_RENDER_MODULE_NAME,
                    NRD_MODULE_NAME,
                    XESS_MODULE_NAME,
                    TONE_MAPPING_MODULE_NAME,
                    POST_RENDER_MODULE_NAME);
        }

        return false;
    }

    private static String getBestAvailablePresetName() {
        if (isPresetAvailable(Presets.RT_NRD_DLSS.key)) {
            return Presets.RT_NRD_DLSS.key;
        }
        if (isPresetAvailable(Presets.RT_NRD_FSR.key)) {
            return Presets.RT_NRD_FSR.key;
        }
        if (isPresetAvailable(Presets.RT_NRD_XESS.key)) {
            return Presets.RT_NRD_XESS.key;
        }
        if (isPresetAvailable(Presets.RT_DLSSRR.key)) {
            return Presets.RT_DLSSRR.key;
        }
        return null;
    }

    private static void assembleBestAvailablePreset(String reason) {
        String fallbackPresetName = getBestAvailablePresetName();
        if (fallbackPresetName == null) {
            throw new RuntimeException("No compatible preset is available.");
        }

        if (reason != null && !reason.isEmpty()) {
            RadianceClient.LOGGER.warn(reason + " Fallback preset: " + fallbackPresetName);
        }
        assemblePresetByKeyInternal(fallbackPresetName);
    }

    public static Module addModule(Module module) {
        INSTANCE.modules.add(module);

        return module;
    }

    public static void connect(ImageConfig src, ImageConfig dst) {
        if (!Objects.equals(src.format, dst.format)) {
            throw new RuntimeException(
                    "Connected format does not match: " + src.format + " != " + dst.format);
        }
        if (!INSTANCE.moduleConnections.containsKey(src)) {
            INSTANCE.moduleConnections.put(src, new ArrayList<>());
        }
        INSTANCE.moduleConnections.get(src).add(dst);
    }

    public static void connectOutput(ImageConfig src) {
        if (!Objects.equals(src.format, "R8G8B8A8_UNORM")) {
            throw new RuntimeException("Invalid output format.");
        }
        src.finalOutput = true;
    }

    public static List<ShaderPackChoice> getAvailableShaderPacks() {
        Map<String, ShaderPackChoice> discovered = new HashMap<>();
        scanShaderPackDirectory(discovered, getInternalShaderPackDirectory(), false);
        scanShaderPackDirectory(discovered, getMinecraftShaderPackDirectory(), true);

        List<ShaderPackChoice> shaderPacks = new ArrayList<>(discovered.values());
        shaderPacks.sort(Comparator
                .<ShaderPackChoice, Boolean>comparing(choice -> !isInternalShaderPackChoice(choice))
                .thenComparingInt(Pipeline::builtInShaderPackRank)
                .thenComparing(choice -> choice.displayName().toLowerCase(Locale.ROOT))
                .thenComparing(ShaderPackChoice::id));
        return Collections.unmodifiableList(shaderPacks);
    }

    private static AttributeConfig findAttribute(Module module, String name) {
        if (module == null || module.attributeConfigs == null) {
            return null;
        }
        for (AttributeConfig attributeConfig : module.attributeConfigs) {
            if (attributeConfig != null && Objects.equals(attributeConfig.name, name)) {
                return attributeConfig;
            }
        }
        return null;
    }

    public static boolean isAttributeVisible(Module module, AttributeConfig attributeConfig) {
        if (module == null || attributeConfig == null || attributeConfig.visibleWhen == null
            || attributeConfig.visibleWhen.isEmpty()) {
            return true;
        }

        Map<String, String> currentValues = new HashMap<>();
        if (module.attributeConfigs != null) {
            for (AttributeConfig currentAttribute : module.attributeConfigs) {
                if (currentAttribute == null || currentAttribute.name == null) {
                    continue;
                }
                currentValues.put(currentAttribute.name, currentAttribute.value);
            }
        }

        for (AttributeConfig.VisibilityCondition condition : attributeConfig.visibleWhen) {
            if (condition == null) {
                continue;
            }

            if (condition.module != null && !condition.module.isEmpty()) {
                boolean modulePresent = false;
                for (Module candidate : INSTANCE.modules) {
                    if (candidate != null && Objects.equals(candidate.name, condition.module)) {
                        modulePresent = true;
                        break;
                    }
                }
                if (!modulePresent) {
                    return false;
                }
            }

            if (condition.attribute == null || condition.attribute.isEmpty()) {
                continue;
            }

            String actualValue = currentValues.get(condition.attribute);
            if (actualValue == null) {
                return false;
            }

            if (condition.values != null && !condition.values.isEmpty()) {
                boolean matched = false;
                for (String expectedValue : condition.values) {
                    if (Objects.equals(expectedValue, actualValue)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            }
        }

        return true;
    }

    public static Module getMainRenderModule() {
        for (Module module : INSTANCE.modules) {
            if (module != null && Objects.equals(module.name, MAIN_RENDER_MODULE_NAME)) {
                return module;
            }
        }
        return null;
    }

    public static boolean supportsSharedEntityGeometry() {
        return sharedEntityGeometrySupported && !isNativeRebuildActive();
    }

    private static boolean supportsSharedEntityGeometry(List<Module> modules) {
        boolean found = false;
        for (Module module : modules) {
            if (!Objects.equals(module.name, MAIN_RENDER_MODULE_NAME)) continue;
            found = true;
            Path current = resolveShaderPackPath(getConfiguredShaderPackPath(module.attributeConfigs));
            if (!current.equals(resolveShaderPackPath(ADVANCED_RAY_TRACING_SHADER_PACK_PATH))
                && !current.equals(resolveShaderPackPath(VANILLA_RAY_TRACING_SHADER_PACK_PATH))) return false;
        }
        return found;
    }

    public static boolean isMainRenderShaderPackPathAttribute(Module module, AttributeConfig attributeConfig) {
        return module != null
                && attributeConfig != null
                && Objects.equals(module.name, MAIN_RENDER_MODULE_NAME)
                && Objects.equals(attributeConfig.name, MAIN_RENDER_SHADER_PACK_PATH_ATTRIBUTE);
    }

    public static boolean isMainRenderShaderPackAttribute(Module module, AttributeConfig attributeConfig) {
        if (isMainRenderShaderPackPathAttribute(module, attributeConfig)) {
            return true;
        }
        return isMainRenderShaderPackDynamicAttribute(module, attributeConfig);
    }

    public static boolean isMainRenderShaderPackDynamicAttribute(Module module, AttributeConfig attributeConfig) {
        if (module == null
                || attributeConfig == null
                || attributeConfig.name == null
                || !Objects.equals(module.name, MAIN_RENDER_MODULE_NAME)) {
            return false;
        }
        return !collectStaticAttributeNames(module).contains(attributeConfig.name);
    }

    public static List<AttributeConfig> getMainRenderShaderPackAttributes() {
        Module module = getMainRenderModule();
        if (module == null) {
            return List.of();
        }

        getModuleAttributes(module);
        List<AttributeConfig> attributes = new ArrayList<>();
        if (module.attributeConfigs == null) {
            return attributes;
        }
        for (AttributeConfig attributeConfig : module.attributeConfigs) {
            if (isMainRenderShaderPackDynamicAttribute(module, attributeConfig)) {
                attributes.add(attributeConfig);
            }
        }
        return attributes;
    }

    private static Path resolveShaderPackPath(String configuredPath) {
        String value = configuredPath == null ? "" : configuredPath.trim();
        Path shaderPackPath = value.isEmpty()
                ? Path.of(DEFAULT_RAY_TRACING_SHADER_PACK_PATH)
                : Path.of(value);
        if (!shaderPackPath.isAbsolute() && RadianceClient.radianceDir != null) {
            shaderPackPath = RadianceClient.radianceDir.resolve(shaderPackPath);
        }
        return shaderPackPath.toAbsolutePath().normalize();
    }

    private static String getConfiguredShaderPackPath(List<AttributeConfig> attributes) {
        if (attributes == null) {
            return "";
        }

        for (AttributeConfig attributeConfig : attributes) {
            if (attributeConfig == null
                    || !Objects.equals(attributeConfig.name, MAIN_RENDER_SHADER_PACK_PATH_ATTRIBUTE)) {
                continue;
            }
            return attributeConfig.value == null ? "" : attributeConfig.value.trim();
        }
        return "";
    }

    private static Path resolveShaderPackAttributeStoragePath(String configuredPath) {
        if (RadianceClient.radianceDir == null) {
            return null;
        }

        try {
            Path shaderPackPath = resolveShaderPackPath(configuredPath);
            String shaderPackFileName = shaderPackPath.getFileName() == null
                    ? "shader-pack"
                    : shaderPackPath.getFileName().toString();
            String safeFileName = shaderPackFileName.replaceAll("[^A-Za-z0-9._-]", "_");
            if (safeFileName.isBlank()) {
                safeFileName = "shader-pack";
            }

            Path storageDirectory = RadianceClient.radianceDir.resolve(SHADER_PACK_SETTINGS_DIRECTORY)
                    .toAbsolutePath()
                    .normalize();
            Path storagePath = storageDirectory.resolve(
                    safeFileName + "-" + sha256Hex(shaderPackPath.toString())
                            + SHADER_PACK_SETTINGS_EXTENSION);
            migrateLegacyShaderPackAttributeStorage(shaderPackPath, storagePath);
            return storagePath;
        } catch (Exception e) {
            RadianceClient.LOGGER.error(
                    "Error while resolving shader pack settings storage path: {}", configuredPath, e);
            return null;
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private static synchronized void migrateLegacyShaderPackAttributeStorage(Path shaderPackPath,
                                                                               Path storagePath) {
        if (Files.exists(storagePath) || shaderPackPath == null || shaderPackPath.getFileName() == null) {
            return;
        }

        Path legacyPath = shaderPackPath.resolveSibling(
                shaderPackPath.getFileName().toString() + LEGACY_SHADER_PACK_SETTINGS_SUFFIX);
        if (!Files.isRegularFile(legacyPath)) {
            return;
        }

        try {
            writeTextAtomically(storagePath, Files.readString(legacyPath, StandardCharsets.UTF_8));
            Files.delete(legacyPath);
        } catch (IOException e) {
            RadianceClient.LOGGER.error(
                    "Failed to migrate shader pack settings from {} to {}.", legacyPath, storagePath, e);
        }
    }

    private static void writeTextAtomically(Path path, String contents) throws IOException {
        Path parent = path.getParent();
        if (parent == null) {
            throw new IOException("Settings file has no parent directory: " + path);
        }

        Files.createDirectories(parent);
        Path temporaryPath = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporaryPath, contents, StandardCharsets.UTF_8);
            try {
                Files.move(temporaryPath, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private static Path getInternalShaderPackDirectory() {
        if (RadianceClient.radianceDir == null) {
            return null;
        }
        return RadianceClient.radianceDir.resolve(INTERNAL_SHADER_PACK_DIRECTORY).toAbsolutePath().normalize();
    }

    private static Path getMinecraftShaderPackDirectory() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameDirectory == null) {
            return null;
        }

        Path shaderPackDirectory = client.gameDirectory.toPath().resolve(MINECRAFT_SHADER_PACK_DIRECTORY);
        try {
            Files.createDirectories(shaderPackDirectory);
        } catch (IOException e) {
            RadianceClient.LOGGER.warn("Failed to create shader pack directory: {}", shaderPackDirectory, e);
            return null;
        }
        return shaderPackDirectory.toAbsolutePath().normalize();
    }

    private static void scanShaderPackDirectory(Map<String, ShaderPackChoice> discovered,
                                                Path directory,
                                                boolean skipInternalPacks) {
        if (discovered == null || directory == null || !Files.isDirectory(directory)) {
            return;
        }

        try (var stream = Files.list(directory)) {
            stream.filter(Pipeline::isShaderPackCandidate)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(path -> {
                        if (skipInternalPacks && isUnderInternalShaderPackDirectory(path)) {
                            return;
                        }
                        ShaderPackChoice choice = readShaderPackChoice(path);
                        if (choice != null) {
                            discovered.putIfAbsent(choice.id(), choice);
                        }
                    });
        } catch (IOException e) {
            RadianceClient.LOGGER.warn("Failed to scan shader pack directory: {}", directory, e);
        }
    }

    private static boolean isShaderPackCandidate(Path path) {
        if (path == null) {
            return false;
        }
        if (Files.isDirectory(path)) {
            return true;
        }
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".zip");
    }

    private static boolean isUnderInternalShaderPackDirectory(Path path) {
        Path internalDirectory = getInternalShaderPackDirectory();
        if (internalDirectory == null || path == null) {
            return false;
        }
        try {
            return path.toAbsolutePath().normalize().startsWith(internalDirectory);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isInternalShaderPackChoice(ShaderPackChoice choice) {
        if (choice == null || choice.relativePath() == null || choice.relativePath().isBlank()) {
            return false;
        }
        try {
            if (Path.of(choice.relativePath()).isAbsolute()) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        String relativePath = choice.relativePath().replace('\\', '/');
        return relativePath.startsWith(INTERNAL_SHADER_PACK_DIRECTORY + "/");
    }

    private static int builtInShaderPackRank(ShaderPackChoice choice) {
        if (choice == null || choice.relativePath() == null) {
            return Integer.MAX_VALUE;
        }
        String relativePath = choice.relativePath().replace('\\', '/');
        if (Objects.equals(relativePath, VANILLA_RAY_TRACING_SHADER_PACK_PATH)) {
            return 0;
        }
        if (Objects.equals(relativePath, ADVANCED_RAY_TRACING_SHADER_PACK_PATH)) {
            return 1;
        }
        return Integer.MAX_VALUE;
    }

    private static ShaderPackChoice readShaderPackChoice(Path shaderPackPath) {
        Path normalizedPath = shaderPackPath.toAbsolutePath().normalize();
        Object config = loadShaderPackDocument(normalizedPath, SHADER_PACK_CONFIG_FILE);
        if (!(config instanceof Map<?, ?>) || !isRadianceShaderPackManifest(config)) {
            return null;
        }

        String configuredPath = toConfiguredShaderPackPath(normalizedPath);
        String displayName = readShaderPackDisplayName(normalizedPath, config);
        return new ShaderPackChoice(normalizedPath.toString(), displayName, configuredPath);
    }

    private static String toConfiguredShaderPackPath(Path shaderPackPath) {
        if (shaderPackPath == null) {
            return "";
        }

        Path normalizedPath = shaderPackPath.toAbsolutePath().normalize();
        if (RadianceClient.radianceDir != null) {
            Path radianceRoot = RadianceClient.radianceDir.toAbsolutePath().normalize();
            if (normalizedPath.startsWith(radianceRoot)) {
                return radianceRoot.relativize(normalizedPath).toString().replace('\\', '/');
            }
        }
        return normalizedPath.toString();
    }

    private static Object loadShaderPackDocument(Path shaderPackPath, String fileName) {
        if (shaderPackPath == null || fileName == null || fileName.isBlank()) {
            return null;
        }

        try {
            if (Files.isDirectory(shaderPackPath)) {
                Path configPath = shaderPackPath.resolve(fileName);
                if (!Files.exists(configPath)) {
                    return null;
                }
                return new Yaml().load(Files.readString(configPath, StandardCharsets.UTF_8));
            }

            if (!Files.exists(shaderPackPath)) {
                return null;
            }

            try (ZipFile zipFile = new ZipFile(shaderPackPath.toFile())) {
                ZipEntry entry = zipFile.getEntry(fileName);
                if (entry == null) {
                    return null;
                }
                try (InputStream in = zipFile.getInputStream(entry)) {
                    return new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            RadianceClient.LOGGER.warn("Failed to read shader pack document: {} from {}", fileName, shaderPackPath, e);
            return null;
        }
    }

    private static boolean isRadianceShaderPackManifest(Object manifest) {
        if (!(manifest instanceof Map<?, ?> root)) {
            return false;
        }

        Object radiance = root.get(SHADER_PACK_MANIFEST_KEY);
        if (radiance instanceof Boolean bool) {
            return bool;
        }
        if (!(radiance instanceof Map<?, ?> radianceConfig)) {
            return false;
        }

        Object shaderPack = radianceConfig.get(SHADER_PACK_MANIFEST_TYPE_KEY);
        if (shaderPack == null) {
            shaderPack = radianceConfig.get("shaderPack");
        }
        if (shaderPack == null) {
            return true;
        }
        if (shaderPack instanceof Boolean bool) {
            return bool;
        }
        if (shaderPack instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return false;
    }

    private static String readShaderPackDisplayName(Path shaderPackPath, Object manifest) {
        String fallback = shaderPackPath.getFileName() == null
                ? "Shader Pack"
                : stripShaderPackExtension(shaderPackPath.getFileName().toString());

        if (!(manifest instanceof Map<?, ?> root)) {
            return fallback;
        }

        Object radiance = root.get(SHADER_PACK_MANIFEST_KEY);
        if (!(radiance instanceof Map<?, ?> radianceConfig)) {
            return fallback;
        }

        Object displayName = radianceConfig.get(SHADER_PACK_MANIFEST_DISPLAY_NAME_KEY);
        if (displayName == null) {
            displayName = radianceConfig.get("displayName");
        }
        if (displayName == null) {
            displayName = radianceConfig.get("name");
        }
        if (displayName instanceof String string && !string.isBlank()) {
            return string;
        }

        return fallback;
    }

    private static String stripShaderPackExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Shader Pack";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    private static boolean readRequiresEmissionFromConfig(Path shaderPackPath) {
        try {
            Object loaded = loadShaderPackDocument(shaderPackPath, SHADER_PACK_CONFIG_FILE);
            if (loaded instanceof Map<?, ?> root) {
                Object value = root.get("requires_emission");
                if (value == null) {
                    value = root.get("requiresEmission");
                }
                if (value instanceof Boolean bool) {
                    return bool;
                }
                if (value instanceof String string) {
                    return Boolean.parseBoolean(string);
                }
            }
        } catch (Exception e) {
            RadianceClient.LOGGER.warn("Failed to read shader pack config: {}", shaderPackPath, e);
        }

        String fileName = shaderPackPath.getFileName() == null ? "" : shaderPackPath.getFileName().toString();
        return fileName.equals("advanced.zip") || fileName.equals("advanced");
    }

    public static boolean shaderPackRequiresEmission(String configuredPath) {
        return readRequiresEmissionFromConfig(resolveShaderPackPath(configuredPath));
    }

    public static boolean isShaderPackSelectable(ShaderPackChoice choice) {
        return choice != null && (!shaderPackRequiresEmission(choice.relativePath()) || Options.collectChunkEmission);
    }

    public static String getShaderPackUnavailabilityReasonTranslationKey(ShaderPackChoice choice) {
        if (choice == null) {
            return null;
        }
        if (shaderPackRequiresEmission(choice.relativePath()) && !Options.collectChunkEmission) {
            return "shader_pack_screen.unavailable_emission";
        }
        return null;
    }

    private static boolean isShaderPackValueSelectable(String configuredPath) {
        return !shaderPackRequiresEmission(configuredPath) || Options.collectChunkEmission;
    }

    private static boolean fallbackUnavailableShaderPacks() {
        boolean changed = false;
        for (Module module : INSTANCE.modules) {
            if (module == null || !Objects.equals(module.name, MAIN_RENDER_MODULE_NAME)) {
                continue;
            }
            AttributeConfig shaderPackPath = findAttribute(module, MAIN_RENDER_SHADER_PACK_PATH_ATTRIBUTE);
            if (shaderPackPath == null || isShaderPackValueSelectable(shaderPackPath.value)) {
                continue;
            }
            shaderPackPath.value = EMISSION_FALLBACK_RAY_TRACING_SHADER_PACK_PATH;
            changed = true;
            RadianceClient.LOGGER.warn("Selected shader pack requires chunk emission collection. Falling back to vanilla PT.");
        }
        return changed;
    }

    public static void ensureSelectedShaderPackAvailable() {
        if (!fallbackUnavailableShaderPacks()) {
            return;
        }
        savePipeline();
        build();
    }

    public static boolean setShaderPack(ShaderPackChoice choice) {
        return setShaderPack(choice, true);
    }

    public static boolean setShaderPack(ShaderPackChoice choice, boolean rebuildNow) {
        if (!isShaderPackSelectable(choice)) {
            return false;
        }

        boolean changed = false;
        for (Module module : INSTANCE.modules) {
            if (module == null || !Objects.equals(module.name, MAIN_RENDER_MODULE_NAME)) {
                continue;
            }
            AttributeConfig shaderPackPath = findAttribute(module, MAIN_RENDER_SHADER_PACK_PATH_ATTRIBUTE);
            if (shaderPackPath == null || Objects.equals(shaderPackPath.value, choice.relativePath())) {
                continue;
            }
            if (!changed) {
                saveDynamicShaderPackAttributes();
            }
            shaderPackPath.value = choice.relativePath();
            getModuleAttributes(module);
            changed = true;
        }

        if (changed && rebuildNow) {
            savePipeline();
            build();
        }
        return changed;
    }

    public static boolean isShaderPackActive(ShaderPackChoice choice) {
        if (choice == null) {
            return false;
        }
        for (Module module : INSTANCE.modules) {
            if (module == null || !Objects.equals(module.name, MAIN_RENDER_MODULE_NAME)) {
                continue;
            }
            AttributeConfig shaderPackPath = findAttribute(module, MAIN_RENDER_SHADER_PACK_PATH_ATTRIBUTE);
            String value = shaderPackPath == null || shaderPackPath.value == null ? "" : shaderPackPath.value.trim();
            if (value.isEmpty() && Objects.equals(choice.relativePath(), DEFAULT_RAY_TRACING_SHADER_PACK_PATH)) {
                return true;
            }
            Path current = resolveShaderPackPath(value);
            Path candidate = resolveShaderPackPath(choice.relativePath());
            return Objects.equals(current, candidate);
        }
        return false;
    }

    public static void build() {
        if (Profiler.ENABLED) {
            Profiler.enter("java.Pipeline.build");
        }
        try {
            boolean built = false;
            try {
                buildInternal();
                built = true;
            } catch (Exception e) {
                RadianceClient.LOGGER.error("Failed to build render pipeline.", e);
                if (isPipelineCompatibilityFailure(e) && tryRebuildCompatiblePipeline(e)) {
                    built = true;
                }
            } finally {
                if (built) {
                    savePipeline();
                }
            }
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static void buildInternal() {
        if (Profiler.ENABLED) {
            Profiler.enter("java.Pipeline.buildInternal");
        }
        try {
            fallbackUnavailableShaderPacks();
            getModuleAttributes();

            Map<ImageConfig, ImageConfig> inputToSourceMap = new HashMap<>();
            for (Map.Entry<ImageConfig, List<ImageConfig>> entry : INSTANCE.moduleConnections.entrySet()) {
                ImageConfig source = entry.getKey();
                for (ImageConfig dest : entry.getValue()) {
                    if (inputToSourceMap.containsKey(dest)) {
                        throw new RuntimeException(
                                "Input config '" + dest.name + "' has multiple sources connected!");
                    }
                    inputToSourceMap.put(dest, source);
                }
            }

            ImageConfig finalOutputConfig = null;
            Module finalModule = null;

            for (Module module : INSTANCE.modules) {
                for (ImageConfig outputConfig : module.outputImageConfigs) {
                    if (outputConfig.finalOutput) {
                        if (finalOutputConfig != null) {
                            throw new RuntimeException(
                                    "Multiple final outputs detected! Only one allows.");
                        }
                        finalOutputConfig = outputConfig;
                        finalModule = module;
                    }
                }
            }

        if (finalOutputConfig == null) {
            throw new RuntimeException("No final output configured.");
        }

        List<Module> sortedModules = new ArrayList<>();
        Set<Module> visited = new HashSet<>();
        Set<Module> visiting = new HashSet<>();

        topologicalSort(finalModule, inputToSourceMap, visited, visiting, sortedModules);

        for (Module sortedModule : sortedModules) {
            for (ImageConfig inputConfig : sortedModule.inputImageConfigs) {
                if (!inputToSourceMap.containsKey(inputConfig)) {
                    throw new RuntimeException(
                            "Module '" + sortedModule.name + "' has unconnected input: " + inputConfig.name);
                }
            }
        }

        List<Integer> imageFormatList = new ArrayList<>();
        Map<ImageConfig, Integer> configToImageIdMap = new HashMap<>();

        int finalFormatId = VulkanConstants.VkFormat.getVkFormatByName(finalOutputConfig.format);
        imageFormatList.add(finalFormatId);
        configToImageIdMap.put(finalOutputConfig, 0);

        for (Module module : sortedModules) {
            for (ImageConfig outputConfig : module.outputImageConfigs) {
                int imageId;
                if (configToImageIdMap.containsKey(outputConfig)) {
                    imageId = configToImageIdMap.get(outputConfig);

                    if (imageId != 0) {
                        throw new RuntimeException();
                    }
                } else {
                    imageId = imageFormatList.size();
                    imageFormatList.add(
                            VulkanConstants.VkFormat.getVkFormatByName(outputConfig.format));
                    configToImageIdMap.put(outputConfig, imageId);
                }

                List<ImageConfig> connectedInputs = INSTANCE.moduleConnections.get(outputConfig);
                if (connectedInputs != null && !connectedInputs.isEmpty()) {
                    for (ImageConfig inputConfig : connectedInputs) {
                        configToImageIdMap.put(inputConfig, imageId);
                    }
                }
            }
        }

        List<List<AttributeConfig>> moduleAttributes = new ArrayList<>();
        for (Module sortedModule : sortedModules) {
            moduleAttributes.add(
                    sortedModule.attributeConfigs != null ? sortedModule.attributeConfigs : new ArrayList<>());
        }

        buildNative(sortedModules, imageFormatList, configToImageIdMap, moduleAttributes);
        sharedEntityGeometrySupported = supportsSharedEntityGeometry(sortedModules);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static boolean isPipelineCompatibilityFailure(Exception e) {
        if (e == null) {
            return false;
        }

        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }

        return message.contains("has unconnected input")
                || message.contains("has multiple sources connected")
                || message.contains("No final output configured")
                || message.contains("Multiple final outputs detected")
                || message.contains("Cycle detected involving module")
                || message.contains("Module with name")
                || message.contains("Unsupported preset");
    }

    private static boolean tryRebuildCompatiblePipeline(Exception cause) {
        saveDynamicShaderPackAttributes();
        PipelineConfigStorage storage = loadConfigStorage();
        String requestedPreset = INSTANCE.activePresetName;
        if (storage != null && storage.presetName != null && !storage.presetName.isBlank()) {
            requestedPreset = storage.presetName;
        }

        String presetToBuild = processPresetName(requestedPreset);
        if (presetToBuild == null) {
            presetToBuild = getBestAvailablePresetName();
        }
        if (presetToBuild == null) {
            RadianceClient.LOGGER.error("Failed to rebuild an incompatible pipeline: no preset is available.", cause);
            return false;
        }

        try {
            RadianceClient.LOGGER.warn(
                    "Stored pipeline is incompatible with current module definitions. Rebuilding preset: {}",
                    presetToBuild);

            INSTANCE.mode = PipelineMode.PRESET;
            assemblePreset(presetToBuild);

            if (storage != null
                    && PipelineMode.fromString(storage.mode) == PipelineMode.PRESET
                    && Objects.equals(processPresetName(storage.presetName), INSTANCE.activePresetName)) {
                applyPresetModuleOverrides(storage.presetModules);
            }

            buildInternal();
            return true;
        } catch (Exception rebuildError) {
            RadianceClient.LOGGER.error("Automatic pipeline rebuild failed.", rebuildError);
            return false;
        }
    }

    private static void topologicalSort(Module current,
                                        Map<ImageConfig, ImageConfig> inputToSourceMap, Set<Module> visited, Set<Module> visiting,
                                        List<Module> result) {
        if (visiting.contains(current)) {
            throw new RuntimeException("Cycle detected involving module: " + current.name);
        }
        if (visited.contains(current)) {
            return;
        }

        visiting.add(current);

        for (ImageConfig inputConfig : current.inputImageConfigs) {
            ImageConfig sourceOutput = inputToSourceMap.get(inputConfig);
            if (sourceOutput != null) {
                Module dependencyModule = sourceOutput.owner;
                topologicalSort(dependencyModule, inputToSourceMap, visited, visiting, result);
            }
        }

        visiting.remove(current);
        visited.add(current);

        result.add(current);
    }

    private static void buildNative(List<Module> modules, List<Integer> formats,
                                    Map<ImageConfig, Integer> imgMap, List<List<AttributeConfig>> moduleAttributes) {
        List<ByteBuffer> allocatedBuffers = new ArrayList<>();

        try {
            int moduleCount = modules.size();

            ByteBuffer formatBuffer = allocAndTrack(allocatedBuffers, formats.size() * 4);
            for (Integer format : formats) {
                formatBuffer.putInt(format);
            }
            formatBuffer.flip();

            ByteBuffer namesPtrBuffer = allocAndTrack(allocatedBuffers, moduleCount * 8);
            ByteBuffer inputsPtrBuffer = allocAndTrack(allocatedBuffers, moduleCount * 8);
            ByteBuffer outputsPtrBuffer = allocAndTrack(allocatedBuffers, moduleCount * 8);
            ByteBuffer attributeCountsBuffer = allocAndTrack(allocatedBuffers, moduleCount * 4);
            ByteBuffer attributesPtrBuffer = allocAndTrack(allocatedBuffers, moduleCount * 8);

            for (int i = 0; i < moduleCount; i++) {
                Module module = modules.get(i);

                byte[] nameBytes = module.name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ByteBuffer nameBuffer = allocAndTrack(allocatedBuffers, nameBytes.length + 1);
                nameBuffer.put(nameBytes);
                nameBuffer.put((byte) 0);
                nameBuffer.flip();
                namesPtrBuffer.putLong(MemoryUtil.memAddress(nameBuffer));

                List<ImageConfig> inputs = module.inputImageConfigs;
                ByteBuffer inputIndices = allocAndTrack(allocatedBuffers, inputs.size() * 4);
                for (ImageConfig inputConfig : inputs) {
                    inputIndices.putInt(imgMap.get(inputConfig));
                }
                inputIndices.flip();
                inputsPtrBuffer.putLong(MemoryUtil.memAddress(inputIndices));

                List<ImageConfig> outputs = module.outputImageConfigs;
                ByteBuffer outputIndices = allocAndTrack(allocatedBuffers, outputs.size() * 4);
                for (ImageConfig outputConfig : outputs) {
                    outputIndices.putInt(imgMap.getOrDefault(outputConfig, -1));
                }
                outputIndices.flip();
                outputsPtrBuffer.putLong(MemoryUtil.memAddress(outputIndices));

                List<AttributeConfig> attributes = moduleAttributes.get(i);
                attributeCountsBuffer.putInt(attributes.size());

                if (!attributes.isEmpty()) {
                    ByteBuffer attributeKeyValuePointers = allocAndTrack(allocatedBuffers,
                            attributes.size() * 2 * 8);
                    for (AttributeConfig attribute : attributes) {
                        byte[] keyBytes = attribute.name.getBytes(StandardCharsets.UTF_8);
                        byte[] valueBytes = (attribute.value != null ? attribute.value : "").getBytes(
                                StandardCharsets.UTF_8);

                        ByteBuffer keyBuffer = allocAndTrack(allocatedBuffers, keyBytes.length + 1);
                        keyBuffer.put(keyBytes).put((byte) 0).flip();

                        ByteBuffer valueBuffer = allocAndTrack(allocatedBuffers, valueBytes.length + 1);
                        valueBuffer.put(valueBytes).put((byte) 0).flip();

                        attributeKeyValuePointers.putLong(MemoryUtil.memAddress(keyBuffer));
                        attributeKeyValuePointers.putLong(MemoryUtil.memAddress(valueBuffer));
                    }
                    attributeKeyValuePointers.flip();
                    attributesPtrBuffer.putLong(MemoryUtil.memAddress(attributeKeyValuePointers));
                } else {
                    attributesPtrBuffer.putLong(0);
                }
            }

            namesPtrBuffer.flip();
            inputsPtrBuffer.flip();
            outputsPtrBuffer.flip();
            attributeCountsBuffer.flip();
            attributesPtrBuffer.flip();

            ByteBuffer params = allocAndTrack(allocatedBuffers, 56);

            params.putInt(moduleCount);
            params.putInt(0);

            params.putLong(MemoryUtil.memAddress(namesPtrBuffer));
            params.putLong(MemoryUtil.memAddress(formatBuffer));
            params.putLong(MemoryUtil.memAddress(inputsPtrBuffer));
            params.putLong(MemoryUtil.memAddress(outputsPtrBuffer));
            params.putLong(MemoryUtil.memAddress(attributeCountsBuffer));
            params.putLong(MemoryUtil.memAddress(attributesPtrBuffer));

            params.flip();

            buildNative(MemoryUtil.memAddress(params));
        } finally {
            for (ByteBuffer buffer : allocatedBuffers) {
                MemoryUtil.memFree(buffer);
            }
        }
    }

    private static ByteBuffer allocAndTrack(List<ByteBuffer> allocatedBuffers, int size) {
        ByteBuffer buffer = MemoryUtil.memAlloc(size);
        allocatedBuffers.add(buffer);
        return buffer;
    }

    public static void assembleDefault() {
        String defaultPresetName = processPresetName(Presets.RT_DLSSRR.key);
        if (defaultPresetName == null) {
            assembleBestAvailablePreset("Default preset is unavailable.");
            return;
        }
        assemblePresetByKeyInternal(defaultPresetName);
    }

    public static void assembleDLSSRR() {
        if (!isPresetAvailable(Presets.RT_DLSSRR.key)) {
            assembleBestAvailablePreset("DLSS preset is unavailable.");
            return;
        }
        assembleDLSSRRInternal();
    }

    private static void assembleDLSSRRInternal() {
        clear();

        Module mainRenderModule = addModule(MAIN_RENDER_MODULE_NAME);

        Module dlssRRModule = addModule(DLSS_RR_MODULE_NAME);

        Module toneMappingModule = addModule(TONE_MAPPING_MODULE_NAME);

        Module postRenderModule = addModule(POST_RENDER_MODULE_NAME);

        mainRenderModule.x = 100;
        mainRenderModule.y = 220;
        dlssRRModule.x = 380;
        dlssRRModule.y = 220;
        toneMappingModule.x = 660;
        toneMappingModule.y = 140;
        postRenderModule.x = 660;
        postRenderModule.y = 300;

        INSTANCE.activePresetName = Presets.RT_DLSSRR.key;

        connect(mainRenderModule.getOutputImageConfig("radiance"),
                dlssRRModule.getInputImageConfig("radiance"));

        connect(mainRenderModule.getOutputImageConfig("diffuse_albedo_metallic"),
                dlssRRModule.getInputImageConfig("diffuse_albedo_metallic"));

        connect(mainRenderModule.getOutputImageConfig("specular_albedo"),
                dlssRRModule.getInputImageConfig("specular_albedo"));

        connect(mainRenderModule.getOutputImageConfig("normal_roughness"),
                dlssRRModule.getInputImageConfig("normal_roughness"));

        connect(mainRenderModule.getOutputImageConfig("motion_vector"),
                dlssRRModule.getInputImageConfig("motion_vector"));

        connect(mainRenderModule.getOutputImageConfig("linear_depth"),
                dlssRRModule.getInputImageConfig("linear_depth"));

        connect(mainRenderModule.getOutputImageConfig("indirect_specular_hit_distance"),
                dlssRRModule.getInputImageConfig("specular_hit_depth"));

        connect(mainRenderModule.getOutputImageConfig("first_hit_depth"),
                dlssRRModule.getInputImageConfig("first_hit_depth"));

        connect(dlssRRModule.getOutputImageConfig("processed"),
                toneMappingModule.getInputImageConfig("denoised_radiance"));
        connect(dlssRRModule.getOutputImageConfig("processed"),
                postRenderModule.getInputImageConfig("hdr_input"));

        connect(dlssRRModule.getOutputImageConfig("upscaled_first_hit_depth"),
                postRenderModule.getInputImageConfig("first_hit_depth"));
        connect(dlssRRModule.getOutputImageConfig("upscaled_motion_vector"),
                postRenderModule.getInputImageConfig("motion_vector"));
        connect(dlssRRModule.getOutputImageConfig("upscaled_normal_roughness"),
                postRenderModule.getInputImageConfig("normal_roughness"));

        connect(toneMappingModule.getOutputImageConfig("mapped_output"),
                postRenderModule.getInputImageConfig("ldr_input"));

        connectOutput(postRenderModule.getOutputImageConfig("post_rendered"));
    }

    public static void assembleNRDFSR() {
        if (!isPresetAvailable(Presets.RT_NRD_FSR.key)) {
            assembleBestAvailablePreset("NRD+FSR preset is unavailable.");
            return;
        }
        assembleNRDFSRInternal();
    }

    public static void assembleNRDDLSS() {
        if (!isPresetAvailable(Presets.RT_NRD_DLSS.key)) {
            assembleBestAvailablePreset("NRD+DLSS preset is unavailable.");
            return;
        }
        assembleNRDDLSSInternal();
    }

    public static void assembleNRDXESS() {
        if (!isPresetAvailable(Presets.RT_NRD_XESS.key)) {
            assembleBestAvailablePreset("NRD+XeSS preset is unavailable.");
            return;
        }
        assembleNRDXESSInternal();
    }

    private static void connectNrdInputs(Module mainRenderModule, Module denoiserModule) {
        connect(mainRenderModule.getOutputImageConfig("direct_diffuse_radiance"),
                denoiserModule.getInputImageConfig("direct_diffuse_radiance"));
        connect(mainRenderModule.getOutputImageConfig("direct_specular_radiance"),
                denoiserModule.getInputImageConfig("direct_specular_radiance"));
        connect(mainRenderModule.getOutputImageConfig("direct_hit_distance"),
                denoiserModule.getInputImageConfig("direct_hit_distance"));
        connect(mainRenderModule.getOutputImageConfig("direct_light_guide_normal_roughness"),
                denoiserModule.getInputImageConfig("direct_light_guide_normal_roughness"));
        connect(mainRenderModule.getOutputImageConfig("direct_light_guide_motion_depth"),
                denoiserModule.getInputImageConfig("direct_light_guide_motion_depth"));

        connect(mainRenderModule.getOutputImageConfig("indirect_diffuse_radiance"),
                denoiserModule.getInputImageConfig("indirect_diffuse_radiance"));
        connect(mainRenderModule.getOutputImageConfig("indirect_specular_radiance"),
                denoiserModule.getInputImageConfig("indirect_specular_radiance"));
        connect(mainRenderModule.getOutputImageConfig("indirect_diffuse_direction"),
                denoiserModule.getInputImageConfig("indirect_diffuse_direction"));
        connect(mainRenderModule.getOutputImageConfig("indirect_specular_direction"),
                denoiserModule.getInputImageConfig("indirect_specular_direction"));
        connect(mainRenderModule.getOutputImageConfig("indirect_diffuse_hit_distance"),
                denoiserModule.getInputImageConfig("indirect_diffuse_hit_distance"));
        connect(mainRenderModule.getOutputImageConfig("indirect_specular_hit_distance"),
                denoiserModule.getInputImageConfig("indirect_specular_hit_distance"));
        connect(mainRenderModule.getOutputImageConfig("normal_roughness"),
                denoiserModule.getInputImageConfig("indirect_guide_normal_roughness"));
        connect(mainRenderModule.getOutputImageConfig("motion_vector"),
                denoiserModule.getInputImageConfig("indirect_guide_motion"));
        connect(mainRenderModule.getOutputImageConfig("linear_depth"),
                denoiserModule.getInputImageConfig("indirect_guide_depth"));

        connect(mainRenderModule.getOutputImageConfig("diffuse_albedo_metallic"),
                denoiserModule.getInputImageConfig("shared_diffuse_albedo_metallic"));
        connect(mainRenderModule.getOutputImageConfig("specular_albedo"),
                denoiserModule.getInputImageConfig("shared_specular_albedo"));

        connect(mainRenderModule.getOutputImageConfig("first_hit_clear"),
                denoiserModule.getInputImageConfig("compose_clear_radiance"));
        connect(mainRenderModule.getOutputImageConfig("first_hit_base_emission"),
                denoiserModule.getInputImageConfig("compose_base_emission"));
        connect(mainRenderModule.getOutputImageConfig("fog_image"),
                denoiserModule.getInputImageConfig("compose_fog"));
    }

    private static void assembleNRDFSRInternal() {
        clear();

        Module mainRenderModule = addModule(MAIN_RENDER_MODULE_NAME);

        Module denoiserModule = addModule(NRD_MODULE_NAME);

        Module upscalerModule = addModule(FSR3_MODULE_NAME);

        Module toneMappingModule = addModule(TONE_MAPPING_MODULE_NAME);

        Module postRenderModule = addModule(POST_RENDER_MODULE_NAME);

        mainRenderModule.x = 100;
        mainRenderModule.y = 220;
        denoiserModule.x = 380;
        denoiserModule.y = 120;
        upscalerModule.x = 660;
        upscalerModule.y = 220;
        toneMappingModule.x = 940;
        toneMappingModule.y = 120;
        postRenderModule.x = 940;
        postRenderModule.y = 300;

        INSTANCE.activePresetName = Presets.RT_NRD_FSR.key;

        connectNrdInputs(mainRenderModule, denoiserModule);

        connect(denoiserModule.getOutputImageConfig("denoised_radiance"),
                upscalerModule.getInputImageConfig("color"));

        connect(mainRenderModule.getOutputImageConfig("linear_depth"),
                upscalerModule.getInputImageConfig("depth"));

        connect(mainRenderModule.getOutputImageConfig("first_hit_depth"),
                upscalerModule.getInputImageConfig("first_hit_depth"));

        connect(mainRenderModule.getOutputImageConfig("motion_vector"),
                upscalerModule.getInputImageConfig("motion_vector"));
        connect(mainRenderModule.getOutputImageConfig("normal_roughness"),
                upscalerModule.getInputImageConfig("normal_roughness"));

        connect(upscalerModule.getOutputImageConfig("upscaled_radiance"),
                toneMappingModule.getInputImageConfig("denoised_radiance"));
        connect(upscalerModule.getOutputImageConfig("upscaled_radiance"),
                postRenderModule.getInputImageConfig("hdr_input"));
        connect(upscalerModule.getOutputImageConfig("upscaled_first_hit_depth"),
                postRenderModule.getInputImageConfig("first_hit_depth"));
        connect(upscalerModule.getOutputImageConfig("upscaled_motion_vector"),
                postRenderModule.getInputImageConfig("motion_vector"));
        connect(upscalerModule.getOutputImageConfig("upscaled_normal_roughness"),
                postRenderModule.getInputImageConfig("normal_roughness"));

        connect(toneMappingModule.getOutputImageConfig("mapped_output"),
                postRenderModule.getInputImageConfig("ldr_input"));

        connectOutput(postRenderModule.getOutputImageConfig("post_rendered"));
    }

    private static void assembleNRDDLSSInternal() {
        clear();

        Module mainRenderModule = addModule(MAIN_RENDER_MODULE_NAME);

        Module denoiserModule = addModule(NRD_MODULE_NAME);

        Module upscalerModule = addModule(DLSS_SR_MODULE_NAME);

        Module toneMappingModule = addModule(TONE_MAPPING_MODULE_NAME);

        Module postRenderModule = addModule(POST_RENDER_MODULE_NAME);

        mainRenderModule.x = 100;
        mainRenderModule.y = 220;
        denoiserModule.x = 380;
        denoiserModule.y = 120;
        upscalerModule.x = 660;
        upscalerModule.y = 220;
        toneMappingModule.x = 940;
        toneMappingModule.y = 120;
        postRenderModule.x = 940;
        postRenderModule.y = 300;

        INSTANCE.activePresetName = Presets.RT_NRD_DLSS.key;

        connectNrdInputs(mainRenderModule, denoiserModule);

        connect(denoiserModule.getOutputImageConfig("denoised_radiance"),
                upscalerModule.getInputImageConfig("color"));

        connect(mainRenderModule.getOutputImageConfig("linear_depth"),
                upscalerModule.getInputImageConfig("depth"));

        connect(mainRenderModule.getOutputImageConfig("first_hit_depth"),
                upscalerModule.getInputImageConfig("first_hit_depth"));

        connect(mainRenderModule.getOutputImageConfig("motion_vector"),
                upscalerModule.getInputImageConfig("motion_vector"));
        connect(mainRenderModule.getOutputImageConfig("normal_roughness"),
                upscalerModule.getInputImageConfig("normal_roughness"));

        connect(upscalerModule.getOutputImageConfig("upscaled_radiance"),
                toneMappingModule.getInputImageConfig("denoised_radiance"));
        connect(upscalerModule.getOutputImageConfig("upscaled_radiance"),
                postRenderModule.getInputImageConfig("hdr_input"));
        connect(upscalerModule.getOutputImageConfig("upscaled_first_hit_depth"),
                postRenderModule.getInputImageConfig("first_hit_depth"));
        connect(upscalerModule.getOutputImageConfig("upscaled_motion_vector"),
                postRenderModule.getInputImageConfig("motion_vector"));
        connect(upscalerModule.getOutputImageConfig("upscaled_normal_roughness"),
                postRenderModule.getInputImageConfig("normal_roughness"));

        connect(toneMappingModule.getOutputImageConfig("mapped_output"),
                postRenderModule.getInputImageConfig("ldr_input"));

        connectOutput(postRenderModule.getOutputImageConfig("post_rendered"));
    }

    private static void assembleNRDXESSInternal() {
        clear();

        Module mainRenderModule = addModule(MAIN_RENDER_MODULE_NAME);

        Module denoiserModule = addModule(NRD_MODULE_NAME);

        Module upscalerModule = addModule(XESS_MODULE_NAME);

        Module toneMappingModule = addModule(TONE_MAPPING_MODULE_NAME);

        Module postRenderModule = addModule(POST_RENDER_MODULE_NAME);

        mainRenderModule.x = 100;
        mainRenderModule.y = 220;
        denoiserModule.x = 380;
        denoiserModule.y = 120;
        upscalerModule.x = 660;
        upscalerModule.y = 220;
        toneMappingModule.x = 940;
        toneMappingModule.y = 120;
        postRenderModule.x = 940;
        postRenderModule.y = 300;

        INSTANCE.activePresetName = Presets.RT_NRD_XESS.key;

        connectNrdInputs(mainRenderModule, denoiserModule);

        connect(denoiserModule.getOutputImageConfig("denoised_radiance"),
                upscalerModule.getInputImageConfig("color"));

        connect(mainRenderModule.getOutputImageConfig("linear_depth"),
                upscalerModule.getInputImageConfig("depth"));

        connect(mainRenderModule.getOutputImageConfig("first_hit_depth"),
                upscalerModule.getInputImageConfig("first_hit_depth"));

        connect(mainRenderModule.getOutputImageConfig("motion_vector"),
                upscalerModule.getInputImageConfig("motion_vector"));
        connect(mainRenderModule.getOutputImageConfig("normal_roughness"),
                upscalerModule.getInputImageConfig("normal_roughness"));

        connect(upscalerModule.getOutputImageConfig("upscaled_radiance"),
                toneMappingModule.getInputImageConfig("denoised_radiance"));
        connect(upscalerModule.getOutputImageConfig("upscaled_radiance"),
                postRenderModule.getInputImageConfig("hdr_input"));
        connect(upscalerModule.getOutputImageConfig("upscaled_first_hit_depth"),
                postRenderModule.getInputImageConfig("first_hit_depth"));
        connect(upscalerModule.getOutputImageConfig("upscaled_motion_vector"),
                postRenderModule.getInputImageConfig("motion_vector"));
        connect(upscalerModule.getOutputImageConfig("upscaled_normal_roughness"),
                postRenderModule.getInputImageConfig("normal_roughness"));

        connect(toneMappingModule.getOutputImageConfig("mapped_output"),
                postRenderModule.getInputImageConfig("ldr_input"));

        connectOutput(postRenderModule.getOutputImageConfig("post_rendered"));
    }

    private static void assemblePresetByKeyInternal(String presetName) {
        if (Objects.equals(presetName, Presets.RT_DLSSRR.key)) {
            assembleDLSSRRInternal();
            return;
        }

        if (Objects.equals(presetName, Presets.RT_NRD_FSR.key)) {
            assembleNRDFSRInternal();
            return;
        }

        if (Objects.equals(presetName, Presets.RT_NRD_DLSS.key)) {
            assembleNRDDLSSInternal();
            return;
        }

        if (Objects.equals(presetName, Presets.RT_NRD_XESS.key)) {
            assembleNRDXESSInternal();
            return;
        }

        throw new RuntimeException("Unsupported preset: " + presetName);
    }

    public static native void buildNative(long params);

    public static native void collectNativeModules();

    public static native boolean isNativeModuleAvailable(String name);

    public static native String getAttributes(String name, String[] attributes, String language);

    public static native boolean isNativeRebuildActive();

    private static String getCurrentLanguageCode() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            var languageManager = client.getLanguageManager();
            if (languageManager != null) {
                String language = languageManager.getSelected();
                if (language != null && !language.isBlank()) {
                    return language.toLowerCase(Locale.ROOT);
                }
            }
            if (client.options != null && client.options.languageCode != null && !client.options.languageCode.isBlank()) {
                return client.options.languageCode.toLowerCase(Locale.ROOT);
            }
        }

        String fallback = Locale.getDefault().toString();
        return fallback == null || fallback.isBlank()
                ? "en_us"
                : fallback.toLowerCase(Locale.ROOT);
    }

    public static void getModuleAttributes() {
        for (Module module : INSTANCE.modules) {
            getModuleAttributes(module);
        }
    }

    public static void getModuleAttributes(Module module) {
        if (module == null) {
            return;
        }

        if (module.staticAttributeConfigs == null) {
            Module.initializeAttributeDefaults(module.attributeConfigs);
            module.staticAttributeConfigs = Module.copyAttributeConfigs(module.attributeConfigs);
        }

        Map<String, String> currentValues = new HashMap<>();
        if (module.attributeConfigs != null) {
            for (AttributeConfig attributeConfig : module.attributeConfigs) {
                if (attributeConfig == null || attributeConfig.name == null) {
                    continue;
                }
                currentValues.put(attributeConfig.name, attributeConfig.value);
            }
        }

        List<AttributeConfig> mergedAttributeConfigs = Module.copyAttributeConfigs(module.staticAttributeConfigs);
        Map<String, String> translations = new HashMap<>();
        Path shaderPackAttributeStoragePath = null;
        boolean preserveCurrentDynamicValues = true;

        if (Objects.equals(module.name, MAIN_RENDER_MODULE_NAME)) {
            List<AttributeConfig> sourceAttributes =
                    module.attributeConfigs != null ? module.attributeConfigs : module.staticAttributeConfigs;
            String configuredPath = getConfiguredShaderPackPath(sourceAttributes);
            shaderPackAttributeStoragePath = resolveShaderPackAttributeStoragePath(configuredPath);

            String normalizedStoragePath = shaderPackAttributeStoragePath == null
                    ? null
                    : shaderPackAttributeStoragePath.toAbsolutePath().normalize().toString();
            preserveCurrentDynamicValues = Objects.equals(
                    module.dynamicAttributeStoragePath,
                    normalizedStoragePath);

            List<String> attributeList = new ArrayList<>();
            if (sourceAttributes != null) {
                for (AttributeConfig attributeConfig : sourceAttributes) {
                    if (attributeConfig == null || attributeConfig.name == null) {
                        continue;
                    }
                    attributeList.add(attributeConfig.name);
                    attributeList.add(attributeConfig.value != null ? attributeConfig.value : "");
                }
            }

            String metadata = getAttributes(
                    module.name,
                    attributeList.toArray(String[]::new),
                    getCurrentLanguageCode());
            if (metadata == null || metadata.isBlank()) {
                RadianceClient.LOGGER.error(
                        "Shader pack attribute metadata is empty for: {}", configuredPath);
                return;
            }

            Object loaded = new Yaml().load(metadata);
            if (!(loaded instanceof Map<?, ?> root)) {
                RadianceClient.LOGGER.error(
                        "Shader pack attribute metadata is invalid for: {}", configuredPath);
                return;
            }

            Object successNode = root.get("success");
            if (!(successNode instanceof Boolean success) || !success) {
                Object errorNode = root.get("error");
                RadianceClient.LOGGER.error(
                        "Failed to load shader pack attribute metadata for {}: {}",
                        configuredPath,
                        errorNode == null ? "unknown error" : Objects.toString(errorNode));
                return;
            }

            Object attributesNode = root.get("attributes");
            if (attributesNode instanceof List<?> attributeNodes) {
                for (Object attributeNode : attributeNodes) {
                    if (!(attributeNode instanceof Map<?, ?> attributeMap)) {
                        continue;
                    }

                    Object name = attributeMap.get("name");
                    Object type = attributeMap.get("type");
                    if (name == null || type == null) {
                        continue;
                    }

                    AttributeConfig attributeConfig = new AttributeConfig();
                    attributeConfig.name = Objects.toString(name);
                    attributeConfig.type = Objects.toString(type);
                    Object defaultValue = attributeMap.containsKey("default_value")
                            ? attributeMap.get("default_value")
                            : attributeMap.get("value");
                    attributeConfig.value = Objects.toString(defaultValue, "");
                    attributeConfig.defaultValue = attributeConfig.value;
                    Object descriptionNode = attributeMap.get("description");
                    if (descriptionNode != null) {
                        attributeConfig.description = Objects.toString(descriptionNode);
                    }
                    Object groupNode = attributeMap.get("group");
                    if (groupNode != null) {
                        attributeConfig.group = Objects.toString(groupNode);
                    }
                    Object performanceImpactNode = attributeMap.containsKey("performanceImpact")
                            ? attributeMap.get("performanceImpact")
                            : attributeMap.get("performance_impact");
                    if (performanceImpactNode != null) {
                        attributeConfig.performanceImpact = Objects.toString(performanceImpactNode);
                    }
                    Object visibleWhenNode = attributeMap.containsKey("visibleWhen")
                            ? attributeMap.get("visibleWhen")
                            : attributeMap.get("visible_when");
                    attributeConfig.visibleWhen = parseVisibilityConditions(visibleWhenNode);
                    Object linkedValuesNode = attributeMap.containsKey("linkedValues")
                            ? attributeMap.get("linkedValues")
                            : attributeMap.get("linked_values");
                    attributeConfig.linkedValues = parseLinkedValues(linkedValuesNode);
                    attributeConfig.preset = parsePresetConfig(attributeMap.get("preset"));
                    mergedAttributeConfigs.add(attributeConfig);
                }
            }

            Object translationsNode = root.get("translations");
            if (translationsNode instanceof Map<?, ?> translationMap) {
                for (Map.Entry<?, ?> entry : translationMap.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }
                    translations.put(
                            Objects.toString(entry.getKey()),
                            Objects.toString(entry.getValue()));
                }
            }

            if (shaderPackAttributeStoragePath != null && Files.exists(shaderPackAttributeStoragePath)) {
                Map<String, String> storedValues = new HashMap<>();
                try {
                    for (String line : Files.readAllLines(shaderPackAttributeStoragePath, StandardCharsets.UTF_8)) {
                        if (line == null || line.isBlank()) {
                            continue;
                        }
                        int separatorIndex = line.indexOf('=');
                        if (separatorIndex <= 0) {
                            continue;
                        }
                        String key = line.substring(0, separatorIndex).trim();
                        if (key.isEmpty()) {
                            continue;
                        }
                        storedValues.put(key, line.substring(separatorIndex + 1));
                    }
                } catch (IOException e) {
                    RadianceClient.LOGGER.error(
                            "Error while loading shader attribute storage: {}",
                            shaderPackAttributeStoragePath,
                            e);
                }

                if (!storedValues.isEmpty()) {
                    for (AttributeConfig attributeConfig : mergedAttributeConfigs) {
                        if (attributeConfig == null || attributeConfig.name == null) {
                            continue;
                        }
                        if (!storedValues.containsKey(attributeConfig.name)) {
                            continue;
                        }
                        attributeConfig.value = storedValues.get(attributeConfig.name);
                    }
                }
            }
        }

        Set<String> staticAttributeNames = preserveCurrentDynamicValues
                ? Set.of()
                : collectStaticAttributeNames(module);
        for (AttributeConfig attributeConfig : mergedAttributeConfigs) {
            if (attributeConfig == null || attributeConfig.name == null) {
                continue;
            }
            if (currentValues.containsKey(attributeConfig.name)) {
                if (!preserveCurrentDynamicValues
                        && !staticAttributeNames.contains(attributeConfig.name)) {
                    continue;
                }
                attributeConfig.value = currentValues.get(attributeConfig.name);
            }
        }

        module.attributeConfigs = mergedAttributeConfigs;
        module.dynamicTranslations.clear();
        module.dynamicTranslations.putAll(translations);
        module.dynamicAttributeStoragePath = shaderPackAttributeStoragePath == null
                ? null
                : shaderPackAttributeStoragePath.toAbsolutePath().normalize().toString();
        reconcilePresetSelectors(module);
    }

    private static Set<String> collectStaticAttributeNames(Module module) {
        Set<String> names = new HashSet<>();
        if (module == null || module.staticAttributeConfigs == null) {
            return names;
        }
        for (AttributeConfig attributeConfig : module.staticAttributeConfigs) {
            if (attributeConfig == null || attributeConfig.name == null) {
                continue;
            }
            names.add(attributeConfig.name);
        }
        return names;
    }

    public static boolean setAttributeValue(Module module, AttributeConfig sourceConfig, String value) {
        if (module == null || sourceConfig == null) {
            return false;
        }

        if (isPresetSelector(sourceConfig)) {
            if (isPresetCustomValue(sourceConfig, value)) {
                return reconcilePresetSelectors(module);
            }

            Map<String, String> profile = sourceConfig.preset.profiles.get(value);
            if (profile == null || profile.isEmpty()) {
                RadianceClient.LOGGER.warn(
                        "Preset attribute '{}' has no profile for '{}'.",
                        sourceConfig.name,
                        value);
                return reconcilePresetSelectors(module);
            }

            boolean changed = setAttributeValueDirect(sourceConfig, value);
            for (Map.Entry<String, String> entry : profile.entrySet()) {
                AttributeConfig target = findAttribute(module, entry.getKey());
                if (target == null) {
                    RadianceClient.LOGGER.warn(
                            "Preset attribute '{}' targets missing attribute '{}'.",
                            sourceConfig.name,
                            entry.getKey());
                    continue;
                }
                changed |= setAttributeValueDirect(target, entry.getValue());
            }
            return reconcilePresetSelectors(module) || changed;
        }

        boolean changed = setAttributeValueDirect(sourceConfig, value);
        changed |= applyLinkedAttributeValuesInternal(module, sourceConfig);
        return reconcilePresetSelectors(module) || changed;
    }

    public static boolean isPresetSelector(AttributeConfig attributeConfig) {
        return attributeConfig != null
                && attributeConfig.preset != null
                && attributeConfig.type != null
                && attributeConfig.type.startsWith("enum:")
                && attributeConfig.preset.customValue != null
                && !attributeConfig.preset.customValue.isBlank()
                && attributeConfig.preset.profiles != null
                && !attributeConfig.preset.profiles.isEmpty();
    }

    public static boolean isPresetCustomValue(AttributeConfig attributeConfig, String value) {
        return isPresetSelector(attributeConfig)
                && Objects.equals(attributeConfig.preset.customValue, value);
    }

    public static boolean reconcilePresetSelectors(Module module) {
        if (module == null || module.attributeConfigs == null) {
            return false;
        }

        boolean changed = false;
        for (AttributeConfig selector : module.attributeConfigs) {
            if (!isPresetSelector(selector)) {
                continue;
            }

            String matchingProfile = findMatchingPresetProfile(module, selector);
            String value = matchingProfile == null ? selector.preset.customValue : matchingProfile;
            changed |= setAttributeValueDirect(selector, value);
        }
        return changed;
    }

    public static boolean applyLinkedAttributeValues(Module module, AttributeConfig sourceConfig) {
        boolean changed = applyLinkedAttributeValuesInternal(module, sourceConfig);
        return reconcilePresetSelectors(module) || changed;
    }

    private static boolean applyLinkedAttributeValuesInternal(Module module, AttributeConfig sourceConfig) {
        if (module == null || sourceConfig == null || sourceConfig.linkedValues == null
                || sourceConfig.linkedValues.isEmpty()) {
            return false;
        }

        Map<String, String> linkedValues = sourceConfig.linkedValues.get(sourceConfig.value);
        if (linkedValues == null || linkedValues.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (Map.Entry<String, String> entry : linkedValues.entrySet()) {
            AttributeConfig target = findAttribute(module, entry.getKey());
            if (target == null) {
                RadianceClient.LOGGER.warn(
                        "Attribute '{}' links missing target '{}'.",
                        sourceConfig.name,
                        entry.getKey());
                continue;
            }
            String value = entry.getValue();
            if (Objects.equals(target.value, value)) {
                continue;
            }
            changed |= setAttributeValueDirect(target, value);
        }

        return changed;
    }

    private static boolean setAttributeValueDirect(AttributeConfig attributeConfig, String value) {
        if (attributeConfig == null || Objects.equals(attributeConfig.value, value)) {
            return false;
        }
        attributeConfig.value = value;
        return true;
    }

    private static String findMatchingPresetProfile(Module module, AttributeConfig selector) {
        for (Map.Entry<String, Map<String, String>> profile : selector.preset.profiles.entrySet()) {
            Map<String, String> targetValues = profile.getValue();
            if (targetValues == null || targetValues.isEmpty()) {
                continue;
            }

            boolean matches = true;
            for (Map.Entry<String, String> targetValue : targetValues.entrySet()) {
                AttributeConfig target = findAttribute(module, targetValue.getKey());
                if (target == null || !attributeValuesMatch(target, targetValue.getValue())) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return profile.getKey();
            }
        }
        return null;
    }

    private static boolean attributeValuesMatch(AttributeConfig attributeConfig, String expectedValue) {
        String actualValue = attributeConfig.value;
        if (Objects.equals(actualValue, expectedValue)) {
            return true;
        }
        if (actualValue == null || expectedValue == null) {
            return false;
        }

        String type = attributeConfig.type == null ? "" : attributeConfig.type.toLowerCase(Locale.ROOT);
        try {
            if (type.equals("int") || type.startsWith("int_range:")) {
                return Integer.parseInt(actualValue.trim()) == Integer.parseInt(expectedValue.trim());
            }
            if (type.equals("float") || type.startsWith("float_range:")) {
                return Float.compare(
                        Float.parseFloat(actualValue.trim()),
                        Float.parseFloat(expectedValue.trim())) == 0;
            }
            if (type.equals("bool")) {
                Boolean actual = parseBooleanAttributeValue(actualValue);
                Boolean expected = parseBooleanAttributeValue(expectedValue);
                return actual != null && expected != null && Objects.equals(actual, expected);
            }
            if (type.equals("vec3")) {
                return vec3ValuesMatch(actualValue, expectedValue);
            }
        } catch (NumberFormatException ignored) {
            return false;
        }
        return false;
    }

    private static Boolean parseBooleanAttributeValue(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "render_pipeline.true", "true", "1" -> true;
            case "render_pipeline.false", "false", "0" -> false;
            default -> null;
        };
    }

    private static boolean vec3ValuesMatch(String actualValue, String expectedValue) {
        String[] actual = actualValue.trim().split("[,\\s]+");
        String[] expected = expectedValue.trim().split("[,\\s]+");
        if (actual.length != 3 || expected.length != 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            try {
                if (Float.compare(Float.parseFloat(actual[i]), Float.parseFloat(expected[i])) != 0) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Map<String, String>> parseLinkedValues(Object node) {
        Map<String, Map<String, String>> linkedValues = new HashMap<>();
        if (!(node instanceof Map<?, ?> valueMap)) {
            return linkedValues;
        }

        for (Map.Entry<?, ?> valueEntry : valueMap.entrySet()) {
            if (valueEntry.getKey() == null || !(valueEntry.getValue() instanceof Map<?, ?> targetMap)) {
                continue;
            }

            Map<String, String> targetValues = new HashMap<>();
            for (Map.Entry<?, ?> targetEntry : targetMap.entrySet()) {
                if (targetEntry.getKey() == null || targetEntry.getValue() == null) {
                    continue;
                }
                targetValues.put(
                        Objects.toString(targetEntry.getKey()),
                        Objects.toString(targetEntry.getValue()));
            }
            linkedValues.put(Objects.toString(valueEntry.getKey()), targetValues);
        }
        return linkedValues;
    }

    private static AttributeConfig.PresetConfig parsePresetConfig(Object node) {
        if (!(node instanceof Map<?, ?> presetMap)) {
            return null;
        }

        Object customValue = presetMap.containsKey("customValue")
                ? presetMap.get("customValue")
                : presetMap.get("custom_value");
        Object profilesNode = presetMap.get("profiles");
        if (customValue == null || !(profilesNode instanceof Map<?, ?> profileMap)) {
            return null;
        }

        AttributeConfig.PresetConfig preset = new AttributeConfig.PresetConfig();
        preset.customValue = Objects.toString(customValue, "");
        preset.profiles = new LinkedHashMap<>();
        for (Map.Entry<?, ?> profileEntry : profileMap.entrySet()) {
            if (profileEntry.getKey() == null || !(profileEntry.getValue() instanceof Map<?, ?> targetMap)) {
                continue;
            }

            Map<String, String> targetValues = new LinkedHashMap<>();
            for (Map.Entry<?, ?> targetEntry : targetMap.entrySet()) {
                if (targetEntry.getKey() == null || targetEntry.getValue() == null) {
                    continue;
                }
                targetValues.put(
                        Objects.toString(targetEntry.getKey()),
                        Objects.toString(targetEntry.getValue()));
            }
            if (!targetValues.isEmpty()) {
                preset.profiles.put(Objects.toString(profileEntry.getKey()), targetValues);
            }
        }
        return isPresetSelectorConfig(preset) ? preset : null;
    }

    private static boolean isPresetSelectorConfig(AttributeConfig.PresetConfig preset) {
        return preset != null
                && preset.customValue != null
                && !preset.customValue.isBlank()
                && preset.profiles != null
                && !preset.profiles.isEmpty();
    }

    private static List<AttributeConfig.VisibilityCondition> parseVisibilityConditions(Object node) {
        List<AttributeConfig.VisibilityCondition> conditions = new ArrayList<>();
        if (!(node instanceof List<?> conditionNodes)) {
            return conditions;
        }

        for (Object conditionNode : conditionNodes) {
            if (!(conditionNode instanceof Map<?, ?> conditionMap)) {
                continue;
            }

            Object attribute = conditionMap.get("attribute");
            Object module = conditionMap.get("module");
            if (attribute == null && module == null) {
                continue;
            }

            AttributeConfig.VisibilityCondition condition = new AttributeConfig.VisibilityCondition();
            condition.attribute = attribute == null ? "" : Objects.toString(attribute, "");
            condition.module = module == null ? "" : Objects.toString(module, "");
            Object valuesNode = conditionMap.containsKey("values") ? conditionMap.get("values")
                    : conditionMap.get("value");
            if (valuesNode instanceof List<?> valuesList) {
                condition.values = new ArrayList<>();
                for (Object value : valuesList) {
                    if (value == null) {
                        continue;
                    }
                    condition.values.add(Objects.toString(value));
                }
            } else if (valuesNode != null) {
                condition.values = new ArrayList<>();
                condition.values.add(Objects.toString(valuesNode));
            }

            conditions.add(condition);
        }

        return conditions;
    }


    public PipelineMode getMode() {
        return mode;
    }

    public String getActivePresetName() {
        return activePresetName;
    }

    public static PipelineMode getPipelineMode() {
        return INSTANCE.mode;
    }

    public static String getActivePreset() {
        return INSTANCE.activePresetName;
    }

    public static void switchToPipelineMode() {
        switchToPipelineMode(true);
    }

    public static void switchToPipelineMode(boolean commitChanges) {
        if (INSTANCE.mode == PipelineMode.PIPELINE) {
            return;
        }

        INSTANCE.mode = PipelineMode.PIPELINE;

        if (commitChanges) {
            savePipeline();
            build();
        }
    }

    public static void switchToPresetMode(String presetName) {
        switchToPresetMode(presetName, true);
    }

    public static void switchToPresetMode(String presetName, boolean commitChanges) {
        saveDynamicShaderPackAttributes();
        List<PresetStoredModule> carryOverModules = capturePresetModules();

        INSTANCE.mode = PipelineMode.PRESET;
        String processedPresetName = processPresetName(presetName);

        assemblePreset(processedPresetName);

        PipelineConfigStorage storage = loadConfigStorage();
        if (storage != null && Objects.equals(storage.mode, PipelineMode.PRESET.name())
                && Objects.equals(storage.presetName, INSTANCE.activePresetName)) {
            applyPresetModuleOverrides(storage.presetModules);
        }

        applyPresetModuleOverrides(carryOverModules);

        if (commitChanges) {
            savePipeline();
            build();
        }
    }

    public static void assemblePreset(String presetName) {
        String processedPresetName = processPresetName(presetName);
        if (processedPresetName == null) {
            assembleBestAvailablePreset("Requested preset is unavailable.");
            return;
        }

        assemblePresetByKeyInternal(processedPresetName);
    }

    public static String processPresetName(String presetName) {
        String requestedPresetName = presetName;
        if (requestedPresetName == null || requestedPresetName.isEmpty()) {
            requestedPresetName = Presets.RT_DLSSRR.key;
        }

        if (!Objects.equals(requestedPresetName, Presets.RT_DLSSRR.key)
                && !Objects.equals(requestedPresetName, Presets.RT_NRD_DLSS.key)
                && !Objects.equals(requestedPresetName, Presets.RT_NRD_FSR.key)
                && !Objects.equals(requestedPresetName, Presets.RT_NRD_XESS.key)) {
            requestedPresetName = Presets.RT_NRD_FSR.key;
        }

        if (isPresetAvailable(requestedPresetName)) {
            return requestedPresetName;
        }

        return getBestAvailablePresetName();
    }

    private static void applyPresetModuleOverrides(List<PresetStoredModule> storedModules) {
        if (storedModules == null || storedModules.isEmpty()) {
            return;
        }

        for (PresetStoredModule storedModule : storedModules) {
            if (storedModule == null || storedModule.entryName == null) {
                continue;
            }

            for (Module module : INSTANCE.modules) {
                if (!Objects.equals(module.name, storedModule.entryName)) {
                    continue;
                }

                applyStoredAttributes(module, storedModule.attributes);
            }
        }
    }

    private static void applyStoredAttributes(Module module, List<StoredAttribute> storedAttributes) {
        if (module == null) {
            return;
        }

        applyStoredAttributeValues(module, module.attributeConfigs, storedAttributes);
        getModuleAttributes(module);
        applyStoredAttributeValues(module, module.attributeConfigs, storedAttributes);
        reconcilePresetSelectors(module);
    }

    private static void applyStoredAttributeValues(Module module,
                                                   List<AttributeConfig> attributeConfigs,
                                                   List<StoredAttribute> storedAttributes) {
        if (attributeConfigs == null || storedAttributes == null) {
            return;
        }
        Set<String> staticAttributeNames = Objects.equals(module.name, MAIN_RENDER_MODULE_NAME)
                ? collectStaticAttributeNames(module)
                : null;

        for (StoredAttribute storedAttribute : storedAttributes) {
            if (storedAttribute == null || storedAttribute.name == null) {
                continue;
            }
            if (staticAttributeNames != null && !staticAttributeNames.contains(storedAttribute.name)) {
                continue;
            }

            for (int i = 0; i < attributeConfigs.size(); i++) {
                var attributeConfig = attributeConfigs.get(i);

                if (!Objects.equals(attributeConfig.name, storedAttribute.name)) {
                    continue;
                }
                if (storedAttribute.type != null && !Objects.equals(attributeConfig.type, storedAttribute.type)) {
                    continue;
                }

                attributeConfig.value = storedAttribute.value;
                break;
            }
        }
    }

    public static synchronized void savePipeline() {
        if (PIPELINE_CONFIG_PATH == null) {
            return;
        }

        PipelineConfigStorage storage = new PipelineConfigStorage();
        storage.mode = INSTANCE.mode.toString();
        storage.presetName = INSTANCE.activePresetName;

        if (INSTANCE.mode == PipelineMode.PIPELINE) {
            storage.pipeline = capturePipelineStorage();
        } else {
            storage.presetModules = capturePresetModules();
        }

        saveDynamicShaderPackAttributes();

        writeConfigStorage(storage);
    }

    private static synchronized void saveDynamicShaderPackAttributes() {
        for (Module module : INSTANCE.modules) {
            if (module == null || !Objects.equals(module.name, MAIN_RENDER_MODULE_NAME) || module.attributeConfigs == null) {
                continue;
            }

            reconcilePresetSelectors(module);

            String configuredPath = getConfiguredShaderPackPath(module.attributeConfigs);
            Path storagePath = resolveShaderPackAttributeStoragePath(configuredPath);

            if (storagePath == null) {
                continue;
            }

            Set<String> staticAttributeNames = collectStaticAttributeNames(module);
            List<StoredAttribute> storedAttributes = new ArrayList<>();
            for (AttributeConfig attributeConfig : module.attributeConfigs) {
                if (attributeConfig == null || attributeConfig.name == null) {
                    continue;
                }
                if (staticAttributeNames.contains(attributeConfig.name)) {
                    continue;
                }

                StoredAttribute storedAttribute = new StoredAttribute();
                storedAttribute.name = attributeConfig.name;
                storedAttribute.value = attributeConfig.value;
                storedAttributes.add(storedAttribute);
            }

            storedAttributes.sort(Comparator.comparing(attribute -> attribute.name == null ? "" : attribute.name));

            StringBuilder builder = new StringBuilder();
            for (StoredAttribute storedAttribute : storedAttributes) {
                builder.append(storedAttribute.name)
                        .append('=')
                        .append(storedAttribute.value == null ? "" : storedAttribute.value)
                        .append('\n');
            }

            try {
                writeTextAtomically(storagePath, builder.toString());
                module.dynamicAttributeStoragePath = storagePath.toAbsolutePath().normalize().toString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static PipelineStorage capturePipelineStorage() {
        PipelineStorage pipelineStorage = new PipelineStorage();
        pipelineStorage.modules = new ArrayList<>();
        pipelineStorage.moduleConnections = new ArrayList<>();

        Map<Module, String> moduleToId = new HashMap<>();
        for (int index = 0; index < INSTANCE.modules.size(); index++) {
            Module module = INSTANCE.modules.get(index);

            String moduleId = "module_" + index;
            moduleToId.put(module, moduleId);

            StoredModule storedModule = new StoredModule();
            storedModule.id = moduleId;
            storedModule.entryName = module.name;
            storedModule.x = module.x;
            storedModule.y = module.y;
            storedModule.attributes = captureAttributes(module);

            pipelineStorage.modules.add(storedModule);
        }

        List<StoredConnection> storedConnections = new ArrayList<>();
        for (Map.Entry<ImageConfig, List<ImageConfig>> entry : INSTANCE.moduleConnections.entrySet()) {
            ImageConfig srcImageConfig = entry.getKey();
            List<ImageConfig> dstImageConfigs = entry.getValue();

            if (srcImageConfig == null || srcImageConfig.owner == null) {
                continue;
            }
            if (dstImageConfigs == null) {
                continue;
            }

            String srcModuleId = moduleToId.get(srcImageConfig.owner);
            if (srcModuleId == null) {
                continue;
            }

            for (ImageConfig dstImageConfig : dstImageConfigs) {
                if (dstImageConfig == null || dstImageConfig.owner == null) {
                    continue;
                }

                String dstModuleId = moduleToId.get(dstImageConfig.owner);
                if (dstModuleId == null) {
                    continue;
                }

                StoredConnection storedConnection = new StoredConnection();
                storedConnection.srcModuleId = srcModuleId;
                storedConnection.srcImageName = srcImageConfig.name;
                storedConnection.dstModuleId = dstModuleId;
                storedConnection.dstImageName = dstImageConfig.name;

                storedConnections.add(storedConnection);
            }
        }

        storedConnections.sort(
                Comparator.comparing((StoredConnection connection) -> connection.srcModuleId)
                        .thenComparing(connection -> connection.srcImageName)
                        .thenComparing(connection -> connection.dstModuleId)
                        .thenComparing(connection -> connection.dstImageName));

        pipelineStorage.moduleConnections.addAll(storedConnections);

        String finalOutputModuleId = null;
        String finalOutputImageName = null;
        for (int index = 0; index < INSTANCE.modules.size(); index++) {
            Module module = INSTANCE.modules.get(index);
            for (ImageConfig outputConfig : module.outputImageConfigs) {
                if (!outputConfig.finalOutput) {
                    continue;
                }
                if (finalOutputModuleId != null) {
                    throw new RuntimeException("Multiple final outputs detected! Only one allows.");
                }
                finalOutputModuleId = "module_" + index;
                finalOutputImageName = outputConfig.name;
            }
        }

        pipelineStorage.finalOutputModuleId = finalOutputModuleId;
        pipelineStorage.finalOutputImageName = finalOutputImageName;

        return pipelineStorage;
    }

    private static List<StoredAttribute> captureAttributes(Module module) {
        List<StoredAttribute> out = new ArrayList<>();
        if (module == null || module.attributeConfigs == null) {
            return out;
        }
        Set<String> staticAttributeNames = Objects.equals(module.name, MAIN_RENDER_MODULE_NAME)
                ? collectStaticAttributeNames(module)
                : null;

        for (AttributeConfig attributeConfig : module.attributeConfigs) {
            if (attributeConfig == null || attributeConfig.name == null) {
                continue;
            }
            if (staticAttributeNames != null && !staticAttributeNames.contains(attributeConfig.name)) {
                continue;
            }
            StoredAttribute storedAttribute = new StoredAttribute();
            storedAttribute.type = attributeConfig.type;
            storedAttribute.name = attributeConfig.name;
            storedAttribute.value = attributeConfig.value;
            out.add(storedAttribute);
        }
        return out;
    }

    private static List<PresetStoredModule> capturePresetModules() {
        List<PresetStoredModule> list = new ArrayList<>();

        for (Module module : INSTANCE.modules) {
            PresetStoredModule storedModule = new PresetStoredModule();
            storedModule.entryName = module.name;
            storedModule.attributes = captureAttributes(module);
            list.add(storedModule);
        }

        list.sort(Comparator.comparing(presetModule -> presetModule.entryName == null ? "" : presetModule.entryName));
        return list;
    }

    private static void writeConfigStorage(PipelineConfigStorage storage) {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setIndent(2);

        Yaml yaml = new Yaml(dumperOptions);
        String yamlText = yaml.dump(storage);

        try {
            writeTextAtomically(PIPELINE_CONFIG_PATH, yamlText);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static PipelineConfigStorage loadConfigStorage() {
        if (PIPELINE_CONFIG_PATH == null || !Files.exists(PIPELINE_CONFIG_PATH)) {
            return null;
        }

        try {
            String yamlText = Files.readString(PIPELINE_CONFIG_PATH, StandardCharsets.UTF_8);

            LoaderOptions loaderOptions = new LoaderOptions();
            TagInspector tagInspector = tag -> tag.getClassName().startsWith("com.radiance.client.pipeline");
            loaderOptions.setTagInspector(tagInspector);

            Constructor constructor = new Constructor(PipelineConfigStorage.class, loaderOptions);
            Yaml yaml = new Yaml(constructor);

            PipelineConfigStorage storage = yaml.load(yamlText);
            if (storage != null && storage.migrateLegacyFields()) {
                writeConfigStorage(storage);
            }
            return storage;
        } catch (Exception e) {
            RadianceClient.LOGGER.error("Error while loading pipeline config.", e);
            return null;
        }
    }

    private static boolean hasUnavailableStoredModules(PipelineStorage pipelineStorage) {
        if (pipelineStorage == null || pipelineStorage.modules == null) {
            return true;
        }

        for (StoredModule storedModule : pipelineStorage.modules) {
            if (storedModule == null || storedModule.entryName == null) {
                continue;
            }
            if (!isModuleAvailable(storedModule.entryName)) {
                return true;
            }
        }

        return false;
    }

    private static String migrateStoredOutputImageName(String moduleName, String imageName) {
        if (imageName == null) {
            return null;
        }
        if (Objects.equals(moduleName, MAIN_RENDER_MODULE_NAME)) {
            return LEGACY_MAIN_RENDER_OUTPUT_IMAGE_NAMES.getOrDefault(imageName, imageName);
        }
        return imageName;
    }

    private static String migrateStoredInputImageName(String moduleName, String imageName) {
        if (imageName == null) {
            return null;
        }
        if (Objects.equals(moduleName, NRD_MODULE_NAME)) {
            return LEGACY_NRD_INPUT_IMAGE_NAMES.getOrDefault(imageName, imageName);
        }
        return imageName;
    }

    private static ImageConfig findImageConfig(List<ImageConfig> imageConfigs, String name) {
        if (imageConfigs == null || name == null) {
            return null;
        }
        for (ImageConfig imageConfig : imageConfigs) {
            if (imageConfig != null && Objects.equals(imageConfig.name, name)) {
                return imageConfig;
            }
        }
        return null;
    }

    private static void applyPipelineStorage(PipelineStorage pipelineStorage) {
        if (pipelineStorage == null) {
            assembleDefault();
            return;
        }

        pipelineStorage.migrateLegacyImageNames();

        if (hasUnavailableStoredModules(pipelineStorage)) {
            RadianceClient.LOGGER.warn("Stored pipeline contains unavailable modules. Falling back to NRD+FSR.");
            assembleNRDFSR();
            return;
        }

        clear();

        if (pipelineStorage.modules == null || pipelineStorage.modules.isEmpty()
                || pipelineStorage.finalOutputModuleId == null || pipelineStorage.finalOutputImageName == null) {
            assembleDefault();
            return;
        }

        Map<String, Module> idToModule = new HashMap<>();

        for (StoredModule storedModule : pipelineStorage.modules) {
            if (storedModule == null || storedModule.id == null || storedModule.entryName == null) {
                continue;
            }

            Module module = addModule(storedModule.entryName);
            module.x = storedModule.x;
            module.y = storedModule.y;

            applyStoredAttributes(module, storedModule.attributes);

            idToModule.put(storedModule.id, module);
        }

        for (Module module : INSTANCE.modules) {
            for (ImageConfig outputConfig : module.outputImageConfigs) {
                outputConfig.finalOutput = false;
            }
        }

        Module finalModule = idToModule.get(pipelineStorage.finalOutputModuleId);
        if (finalModule == null) {
            assembleDefault();
            return;
        }

        ImageConfig finalImageConfig = findImageConfig(finalModule.outputImageConfigs,
                pipelineStorage.finalOutputImageName);
        if (finalImageConfig == null) {
            RadianceClient.LOGGER.warn("Stored pipeline final output {} is unavailable on module {}.",
                    pipelineStorage.finalOutputImageName, finalModule.name);
            assembleDefault();
            return;
        }

        finalImageConfig.finalOutput = true;

        if (pipelineStorage.moduleConnections != null) {
            for (StoredConnection storedConnection : pipelineStorage.moduleConnections) {
                if (storedConnection == null) {
                    continue;
                }
                if (storedConnection.srcModuleId == null || storedConnection.dstModuleId == null) {
                    continue;
                }
                if (storedConnection.srcImageName == null || storedConnection.dstImageName == null) {
                    continue;
                }

                Module srcModule = idToModule.get(storedConnection.srcModuleId);
                Module dstModule = idToModule.get(storedConnection.dstModuleId);

                if (srcModule == null || dstModule == null) {
                    continue;
                }

                ImageConfig srcImageConfig = findImageConfig(srcModule.outputImageConfigs,
                        storedConnection.srcImageName);
                ImageConfig dstImageConfig = findImageConfig(dstModule.inputImageConfigs,
                        storedConnection.dstImageName);

                if (srcImageConfig == null || dstImageConfig == null) {
                    RadianceClient.LOGGER.warn(
                            "Stored pipeline connection {}.{} -> {}.{} is unavailable. Falling back to the default pipeline.",
                            srcModule.name, storedConnection.srcImageName,
                            dstModule.name, storedConnection.dstImageName);
                    assembleDefault();
                    return;
                }

                connect(srcImageConfig, dstImageConfig);
            }
        }
    }

    public static void loadPipeline() {
        PipelineConfigStorage storage = loadConfigStorage();

        if (storage == null) {
            INSTANCE.mode = PipelineMode.PRESET;
            assembleDefault();
            savePipeline();
            return;
        }

        PipelineMode loadedMode = PipelineMode.fromString(storage.mode);
        INSTANCE.mode = loadedMode;
        if (storage.presetName != null && !storage.presetName.isEmpty()) {
            INSTANCE.activePresetName = processPresetName(storage.presetName);
        }

        if (loadedMode == PipelineMode.PRESET) {
            assemblePreset(INSTANCE.activePresetName);
            applyPresetModuleOverrides(storage.presetModules);
            build();
            return;
        }

        if (storage.pipeline != null) {
            applyPipelineStorage(storage.pipeline);
            build();
            return;
        }

        assembleDefault();
        build();
    }

    public Map<String, ModuleEntry> getModuleEntries() {
        return moduleEntries;
    }

    public List<Module> getModules() {
        return modules;
    }

    public Map<ImageConfig, List<ImageConfig>> getModuleConnections() {
        return moduleConnections;
    }

    public enum PipelineMode {
        PIPELINE,
        PRESET;

        static PipelineMode fromString(String s) {
            if (s == null) {
                return PRESET;
            }
            try {
                return PipelineMode.valueOf(s.toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                return PRESET;
            }
        }
    }

    public static class PipelineConfigStorage {
        public String mode;
        public String presetName;
        public PipelineStorage pipeline;
        public List<PresetStoredModule> presetModules;

        public List<StoredModule> modules;
        public List<StoredConnection> moduleConnections;
        public String finalOutputModuleId;
        public String finalOutputImageName;

        boolean migrateLegacyFields() {
            boolean migrated = false;
            if (pipeline == null && modules != null && !modules.isEmpty()) {
                PipelineStorage ps = new PipelineStorage();
                ps.modules = modules;
                ps.moduleConnections = moduleConnections != null ? moduleConnections : new ArrayList<>();
                ps.finalOutputModuleId = finalOutputModuleId;
                ps.finalOutputImageName = finalOutputImageName;
                pipeline = ps;
                migrated = true;

                if (mode == null) {
                    mode = "PIPELINE";
                }
            }
            if (pipeline != null) {
                migrated |= pipeline.migrateLegacyImageNames();
            }
            return migrated;
        }
    }

    public static class PresetStoredModule {
        public String entryName;
        public List<StoredAttribute> attributes;
    }

    public static class PipelineStorage {
        public List<StoredModule> modules;
        public List<StoredConnection> moduleConnections;
        public String finalOutputModuleId;
        public String finalOutputImageName;

        boolean migrateLegacyImageNames() {
            if (modules == null || modules.isEmpty()) {
                return false;
            }

            Map<String, String> moduleNames = new HashMap<>();
            for (StoredModule module : modules) {
                if (module != null && module.id != null && module.entryName != null) {
                    moduleNames.put(module.id, module.entryName);
                }
            }

            boolean migrated = false;
            String migratedFinalOutput = migrateStoredOutputImageName(
                    moduleNames.get(finalOutputModuleId), finalOutputImageName);
            if (!Objects.equals(finalOutputImageName, migratedFinalOutput)) {
                finalOutputImageName = migratedFinalOutput;
                migrated = true;
            }

            if (moduleConnections == null) {
                return migrated;
            }
            for (StoredConnection connection : moduleConnections) {
                if (connection == null) {
                    continue;
                }

                String migratedSource = migrateStoredOutputImageName(
                        moduleNames.get(connection.srcModuleId), connection.srcImageName);
                String migratedDestination = migrateStoredInputImageName(
                        moduleNames.get(connection.dstModuleId), connection.dstImageName);
                if (!Objects.equals(connection.srcImageName, migratedSource)) {
                    connection.srcImageName = migratedSource;
                    migrated = true;
                }
                if (!Objects.equals(connection.dstImageName, migratedDestination)) {
                    connection.dstImageName = migratedDestination;
                    migrated = true;
                }
            }
            return migrated;
        }
    }

    public static class StoredModule {
        public String id;
        public String entryName;
        public double x;
        public double y;
        public List<StoredAttribute> attributes;
    }

    public static class StoredAttribute {
        public String type;
        public String name;
        public String value;
    }

    public static class StoredConnection {
        public String srcModuleId;
        public String srcImageName;
        public String dstModuleId;
        public String dstImageName;
    }
}
