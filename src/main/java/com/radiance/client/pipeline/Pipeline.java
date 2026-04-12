package com.radiance.client.pipeline;

import com.radiance.client.RadianceClient;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.pipeline.config.AttributeConfig;
import com.radiance.client.pipeline.config.ImageConfig;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.lwjgl.system.MemoryUtil;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.inspector.TagInspector;

public class Pipeline {

    public static Pipeline INSTANCE = new Pipeline();
    private static final String RAY_TRACING_MODULE_NAME = "render_pipeline.module.ray_tracing.name";
    private static final String DLSS_MODULE_NAME = "render_pipeline.module.dlss.name";
    private static final String NRD_MODULE_NAME = "render_pipeline.module.nrd.name";
    private static final String TEMPORAL_ACCUMULATION_MODULE_NAME = "render_pipeline.module.temporal_accumulation.name";
    private static final String FSR3_MODULE_NAME = "render_pipeline.module.fsr_upscaler.name";
    private static final String XESS_MODULE_NAME = "render_pipeline.module.xess_sr.name";
    private static final String TONE_MAPPING_MODULE_NAME = "render_pipeline.module.tone_mapping.name";
    private static final String POST_RENDER_MODULE_NAME = "render_pipeline.module.post_render.name";
    private static Path PIPELINE_CONFIG_PATH = null;
    private final List<Module> modules = new ArrayList<>();
    private final Map<ImageConfig, List<ImageConfig>> moduleConnections = new HashMap<>();
    private Map<String, ModuleEntry> moduleEntries;

    private PipelineMode mode = PipelineMode.PRESET;
    private String activePresetName = null;

    private Pipeline() {
    }

    public static void initFolderPath(Path folderPath) {
        PIPELINE_CONFIG_PATH = folderPath.resolve("pipeline.yaml");
    }

    public static void reloadAllModuleEntries() {
        try {
            INSTANCE.moduleEntries = ModuleEntry.loadAllModuleEntries();

//            System.out.println("Loaded " + INSTANCE.moduleEntries.size() + " module entries.");
//            for (ModuleEntry entry : INSTANCE.moduleEntries.values()) {
//                System.out.println(entry);
//            }
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
                    RAY_TRACING_MODULE_NAME,
                    DLSS_MODULE_NAME,
                    TONE_MAPPING_MODULE_NAME,
                    POST_RENDER_MODULE_NAME);
        }

        if (Objects.equals(presetName, Presets.RT_NRD.key)) {
            return areModulesAvailable(
                    RAY_TRACING_MODULE_NAME,
                    NRD_MODULE_NAME,
                    TEMPORAL_ACCUMULATION_MODULE_NAME,
                    TONE_MAPPING_MODULE_NAME,
                    POST_RENDER_MODULE_NAME);
        }

        if (Objects.equals(presetName, Presets.RT_NRD_FSR.key)) {
            return areModulesAvailable(
                    RAY_TRACING_MODULE_NAME,
                    NRD_MODULE_NAME,
                    FSR3_MODULE_NAME,
                    TONE_MAPPING_MODULE_NAME,
                    POST_RENDER_MODULE_NAME);
        }

        if (Objects.equals(presetName, Presets.RT_NRD_XESS.key)) {
            return areModulesAvailable(
                    RAY_TRACING_MODULE_NAME,
                    NRD_MODULE_NAME,
                    XESS_MODULE_NAME,
                    TONE_MAPPING_MODULE_NAME,
                    POST_RENDER_MODULE_NAME);
        }

        return false;
    }

    private static String getBestAvailablePresetName() {
        if (isPresetAvailable(Presets.RT_NRD_FSR.key)) {
            return Presets.RT_NRD_FSR.key;
        }
        if (isPresetAvailable(Presets.RT_NRD_XESS.key)) {
            return Presets.RT_NRD_XESS.key;
        }
        if (isPresetAvailable(Presets.RT_NRD.key)) {
            return Presets.RT_NRD.key;
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

    public static void build() {
        try {
            Map<ImageConfig, ImageConfig> dstTosrcMap = new HashMap<>();
            for (Map.Entry<ImageConfig, List<ImageConfig>> entry : INSTANCE.moduleConnections.entrySet()) {
                ImageConfig source = entry.getKey();
                for (ImageConfig dest : entry.getValue()) {
                    if (dstTosrcMap.containsKey(dest)) {
                        throw new RuntimeException(
                                "Input config '" + dest.name + "' has multiple sources connected!");
                    }
                    dstTosrcMap.put(dest, source);
                }
            }

            ImageConfig finalOutputConfig = null;
            Module finalModule = null;

            for (Module module : INSTANCE.modules) {
                for (ImageConfig conf : module.outputImageConfigs) {
                    if (conf.finalOutput) {
                        if (finalOutputConfig != null) {
                            throw new RuntimeException(
                                    "Multiple final outputs detected! Only one allows.");
                        }
                        finalOutputConfig = conf;
                        finalModule = module;
                    }
                }
            }

            if (finalOutputConfig == null) {
                throw new RuntimeException("No final output configured.");
            }

            // topological sort
            List<Module> sortedModules = new ArrayList<>();
            Set<Module> visited = new HashSet<>();
            Set<Module> visiting = new HashSet<>();

            topologicalSort(finalModule, dstTosrcMap, visited, visiting, sortedModules);

            // integrity check
            for (Module m : sortedModules) {
                for (ImageConfig inputConf : m.inputImageConfigs) {
                    if (!dstTosrcMap.containsKey(inputConf)) {
                        throw new RuntimeException(
                                "Module '" + m.name + "' has unconnected input: " + inputConf.name);
                    }
                }
            }

            // image list
            List<Integer> imageFormatList = new ArrayList<>();
            Map<ImageConfig, Integer> configToImageIdMap = new HashMap<>();

            int finalFmtId = VulkanConstants.VkFormat.getVkFormatByName(finalOutputConfig.format);
            imageFormatList.add(finalFmtId);
            configToImageIdMap.put(finalOutputConfig, 0);

            for (Module module : sortedModules) {
                for (ImageConfig outConfig : module.outputImageConfigs) {
                    int imgId;
                    if (configToImageIdMap.containsKey(outConfig)) {
                        imgId = configToImageIdMap.get(outConfig);

                        if (imgId != 0) {
                            throw new RuntimeException();
                        }
                    } else {
                        imgId = imageFormatList.size();
                        imageFormatList.add(
                                VulkanConstants.VkFormat.getVkFormatByName(outConfig.format));
                        configToImageIdMap.put(outConfig, imgId);
                    }

                    List<ImageConfig> connectedInputs = INSTANCE.moduleConnections.get(outConfig);
                    if (connectedInputs != null && !connectedInputs.isEmpty()) {
                        for (ImageConfig inputConf : connectedInputs) {
                            configToImageIdMap.put(inputConf, imgId);
                        }
                    }
                }
            }

            List<List<AttributeConfig>> moduleAttributes = new ArrayList<>();
            for (Module m : sortedModules) {
                moduleAttributes.add(
                        m.attributeConfigs != null ? m.attributeConfigs : new ArrayList<>());
            }

            buildNative(sortedModules, imageFormatList, configToImageIdMap, moduleAttributes);
        } catch (Exception e) {
            RadianceClient.LOGGER.error(e.toString());
            Pipeline.loadPipeline();
        } finally {
            savePipeline();
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

        for (ImageConfig inputConf : current.inputImageConfigs) {
            ImageConfig sourceOutput = inputToSourceMap.get(inputConf);
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
            for (Integer fmt : formats) {
                formatBuffer.putInt(fmt);
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
                for (ImageConfig inConfig : inputs) {
                    inputIndices.putInt(imgMap.get(inConfig));
                }
                inputIndices.flip();
                inputsPtrBuffer.putLong(MemoryUtil.memAddress(inputIndices));

                List<ImageConfig> outputs = module.outputImageConfigs;
                ByteBuffer outputIndices = allocAndTrack(allocatedBuffers, outputs.size() * 4);
                for (ImageConfig outConfig : outputs) {
                    outputIndices.putInt(imgMap.getOrDefault(outConfig, -1));
                }
                outputIndices.flip();
                outputsPtrBuffer.putLong(MemoryUtil.memAddress(outputIndices));

                List<AttributeConfig> attrs = moduleAttributes.get(i);
                attributeCountsBuffer.putInt(attrs.size());

                if (!attrs.isEmpty()) {
                    ByteBuffer attrKVPointers = allocAndTrack(allocatedBuffers,
                            attrs.size() * 2 * 8);
                    for (AttributeConfig attr : attrs) {
                        byte[] kBytes = attr.name.getBytes(StandardCharsets.UTF_8);
                        byte[] vBytes = (attr.value != null ? attr.value : "").getBytes(
                                StandardCharsets.UTF_8);

                        ByteBuffer kBuf = allocAndTrack(allocatedBuffers, kBytes.length + 1);
                        kBuf.put(kBytes).put((byte) 0).flip();

                        ByteBuffer vBuf = allocAndTrack(allocatedBuffers, vBytes.length + 1);
                        vBuf.put(vBytes).put((byte) 0).flip();

                        attrKVPointers.putLong(MemoryUtil.memAddress(kBuf));
                        attrKVPointers.putLong(MemoryUtil.memAddress(vBuf));
                    }
                    attrKVPointers.flip();
                    attributesPtrBuffer.putLong(MemoryUtil.memAddress(attrKVPointers));
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
            params.putInt(0); // Padding

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

        Module rayTracingModule = addModule(RAY_TRACING_MODULE_NAME);

        Module dlssModule = addModule(DLSS_MODULE_NAME);

        Module toneMappingModule = addModule(TONE_MAPPING_MODULE_NAME);

        Module postRenderModule = addModule(POST_RENDER_MODULE_NAME);

        rayTracingModule.x = 100;
        rayTracingModule.y = 220;
        dlssModule.x = 380;
        dlssModule.y = 220;
        toneMappingModule.x = 660;
        toneMappingModule.y = 140;
        postRenderModule.x = 660;
        postRenderModule.y = 300;

        INSTANCE.activePresetName = Presets.RT_DLSSRR.key;

        connect(rayTracingModule.getOutputImageConfig("radiance"),
                dlssModule.getInputImageConfig("radiance"));

        connect(rayTracingModule.getOutputImageConfig("diffuse_albedo_metallic"),
                dlssModule.getInputImageConfig("diffuse_albedo_metallic"));

        connect(rayTracingModule.getOutputImageConfig("specular_albedo"),
                dlssModule.getInputImageConfig("specular_albedo"));

        connect(rayTracingModule.getOutputImageConfig("normal_roughness"),
                dlssModule.getInputImageConfig("normal_roughness"));

        connect(rayTracingModule.getOutputImageConfig("motion_vector"),
                dlssModule.getInputImageConfig("motion_vector"));

        connect(rayTracingModule.getOutputImageConfig("linear_depth"),
                dlssModule.getInputImageConfig("linear_depth"));

        connect(rayTracingModule.getOutputImageConfig("specular_hit_depth"),
                dlssModule.getInputImageConfig("specular_hit_depth"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_depth"),
                dlssModule.getInputImageConfig("first_hit_depth"));

        connect(dlssModule.getOutputImageConfig("processed"),
                toneMappingModule.getInputImageConfig("denoised_radiance"));

        connect(dlssModule.getOutputImageConfig("upscaled_first_hit_depth"),
                postRenderModule.getInputImageConfig("first_hit_depth"));

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

    public static void assembleNRDXESS() {
        if (!isPresetAvailable(Presets.RT_NRD_XESS.key)) {
            assembleBestAvailablePreset("NRD+XeSS preset is unavailable.");
            return;
        }
        assembleNRDXESSInternal();
    }

    private static void assembleNRDFSRInternal() {
        clear();

        Module rayTracingModule = addModule(RAY_TRACING_MODULE_NAME);

        Module denoiserModule = addModule(NRD_MODULE_NAME);

        Module upscalerModule = addModule(FSR3_MODULE_NAME);

        Module toneMappingModule = addModule(TONE_MAPPING_MODULE_NAME);

        Module postRenderModule = addModule(POST_RENDER_MODULE_NAME);

        rayTracingModule.x = 100;
        rayTracingModule.y = 220;
        denoiserModule.x = 380;
        denoiserModule.y = 120;
        upscalerModule.x = 660;
        upscalerModule.y = 220;
        toneMappingModule.x = 940;
        toneMappingModule.y = 120;
        postRenderModule.x = 940;
        postRenderModule.y = 300;

        INSTANCE.activePresetName = Presets.RT_NRD_FSR.key;

        connect(rayTracingModule.getOutputImageConfig("first_hit_diffuse_indirect_light"),
                denoiserModule.getInputImageConfig("diffuse_radiance"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_specular"),
                denoiserModule.getInputImageConfig("specular_radiance"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_diffuse_direct_light"),
                denoiserModule.getInputImageConfig("direct_radiance"));

        connect(rayTracingModule.getOutputImageConfig("diffuse_albedo_metallic"),
                denoiserModule.getInputImageConfig("diffuse_albedo"));

        connect(rayTracingModule.getOutputImageConfig("specular_albedo"),
                denoiserModule.getInputImageConfig("specular_albedo"));

        connect(rayTracingModule.getOutputImageConfig("normal_roughness"),
                denoiserModule.getInputImageConfig("normal_roughness"));

        connect(rayTracingModule.getOutputImageConfig("motion_vector"),
                denoiserModule.getInputImageConfig("motion_vector"));

        connect(rayTracingModule.getOutputImageConfig("linear_depth"),
                denoiserModule.getInputImageConfig("linear_depth"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_depth"),
                denoiserModule.getInputImageConfig("diffuseHitDepthImage"));

        connect(rayTracingModule.getOutputImageConfig("specular_hit_depth"),
                denoiserModule.getInputImageConfig("specularHitDepthImage"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_clear"),
                denoiserModule.getInputImageConfig("first_hit_clear"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_base_emission"),
                denoiserModule.getInputImageConfig("first_hit_base_emission"));

        connect(rayTracingModule.getOutputImageConfig("fog_image"),
                denoiserModule.getInputImageConfig("fog_image"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_refraction"),
                denoiserModule.getInputImageConfig("first_hit_refraction"));

        connect(denoiserModule.getOutputImageConfig("denoised_radiance"),
                upscalerModule.getInputImageConfig("color"));

        connect(rayTracingModule.getOutputImageConfig("linear_depth"),
                upscalerModule.getInputImageConfig("depth"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_depth"),
                upscalerModule.getInputImageConfig("first_hit_depth"));

        connect(rayTracingModule.getOutputImageConfig("motion_vector"),
                upscalerModule.getInputImageConfig("motion_vector"));

        connect(upscalerModule.getOutputImageConfig("upscaled_radiance"),
                toneMappingModule.getInputImageConfig("denoised_radiance"));
        connect(upscalerModule.getOutputImageConfig("upscaled_first_hit_depth"),
                postRenderModule.getInputImageConfig("first_hit_depth"));

        connect(toneMappingModule.getOutputImageConfig("mapped_output"),
                postRenderModule.getInputImageConfig("ldr_input"));

        connectOutput(postRenderModule.getOutputImageConfig("post_rendered"));
    }

    private static void assembleNRDXESSInternal() {
        clear();

        Module rayTracingModule = addModule(RAY_TRACING_MODULE_NAME);

        Module denoiserModule = addModule(NRD_MODULE_NAME);

        Module upscalerModule = addModule(XESS_MODULE_NAME);

        Module toneMappingModule = addModule(TONE_MAPPING_MODULE_NAME);

        Module postRenderModule = addModule(POST_RENDER_MODULE_NAME);

        rayTracingModule.x = 100;
        rayTracingModule.y = 220;
        denoiserModule.x = 380;
        denoiserModule.y = 120;
        upscalerModule.x = 660;
        upscalerModule.y = 220;
        toneMappingModule.x = 940;
        toneMappingModule.y = 120;
        postRenderModule.x = 940;
        postRenderModule.y = 300;

        INSTANCE.activePresetName = Presets.RT_NRD_XESS.key;

        connect(rayTracingModule.getOutputImageConfig("first_hit_diffuse_indirect_light"),
                denoiserModule.getInputImageConfig("diffuse_radiance"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_specular"),
                denoiserModule.getInputImageConfig("specular_radiance"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_diffuse_direct_light"),
                denoiserModule.getInputImageConfig("direct_radiance"));

        connect(rayTracingModule.getOutputImageConfig("diffuse_albedo_metallic"),
                denoiserModule.getInputImageConfig("diffuse_albedo"));

        connect(rayTracingModule.getOutputImageConfig("specular_albedo"),
                denoiserModule.getInputImageConfig("specular_albedo"));

        connect(rayTracingModule.getOutputImageConfig("normal_roughness"),
                denoiserModule.getInputImageConfig("normal_roughness"));

        connect(rayTracingModule.getOutputImageConfig("motion_vector"),
                denoiserModule.getInputImageConfig("motion_vector"));

        connect(rayTracingModule.getOutputImageConfig("linear_depth"),
                denoiserModule.getInputImageConfig("linear_depth"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_depth"),
                denoiserModule.getInputImageConfig("diffuseHitDepthImage"));

        connect(rayTracingModule.getOutputImageConfig("specular_hit_depth"),
                denoiserModule.getInputImageConfig("specularHitDepthImage"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_clear"),
                denoiserModule.getInputImageConfig("first_hit_clear"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_base_emission"),
                denoiserModule.getInputImageConfig("first_hit_base_emission"));

        connect(rayTracingModule.getOutputImageConfig("fog_image"),
                denoiserModule.getInputImageConfig("fog_image"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_refraction"),
                denoiserModule.getInputImageConfig("first_hit_refraction"));

        connect(denoiserModule.getOutputImageConfig("denoised_radiance"),
                upscalerModule.getInputImageConfig("color"));

        connect(rayTracingModule.getOutputImageConfig("linear_depth"),
                upscalerModule.getInputImageConfig("depth"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_depth"),
                upscalerModule.getInputImageConfig("first_hit_depth"));

        connect(rayTracingModule.getOutputImageConfig("motion_vector"),
                upscalerModule.getInputImageConfig("motion_vector"));

        connect(upscalerModule.getOutputImageConfig("upscaled_radiance"),
                toneMappingModule.getInputImageConfig("denoised_radiance"));
        connect(upscalerModule.getOutputImageConfig("upscaled_first_hit_depth"),
                postRenderModule.getInputImageConfig("first_hit_depth"));

        connect(toneMappingModule.getOutputImageConfig("mapped_output"),
                postRenderModule.getInputImageConfig("ldr_input"));

        connectOutput(postRenderModule.getOutputImageConfig("post_rendered"));
    }

    public static void assembleNRD() {
        if (!isPresetAvailable(Presets.RT_NRD.key)) {
            assembleBestAvailablePreset("NRD preset is unavailable.");
            return;
        }
        assembleNRDInternal();
    }

    private static void assembleNRDInternal() {
        clear();

        Module rayTracingModule = addModule(RAY_TRACING_MODULE_NAME);

        Module denoiserModule = addModule(NRD_MODULE_NAME);

        Module temporalAccumulationModule = addModule(TEMPORAL_ACCUMULATION_MODULE_NAME);

        Module toneMappingModule = addModule(TONE_MAPPING_MODULE_NAME);

        Module postRenderModule = addModule(POST_RENDER_MODULE_NAME);

        rayTracingModule.x = 100;
        rayTracingModule.y = 220;
        denoiserModule.x = 380;
        denoiserModule.y = 120;
        temporalAccumulationModule.x = 660;
        temporalAccumulationModule.y = 120;
        toneMappingModule.x = 940;
        toneMappingModule.y = 120;
        postRenderModule.x = 940;
        postRenderModule.y = 300;

        INSTANCE.activePresetName = Presets.RT_NRD.key;

        connect(rayTracingModule.getOutputImageConfig("first_hit_diffuse_indirect_light"),
                denoiserModule.getInputImageConfig("diffuse_radiance"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_specular"),
                denoiserModule.getInputImageConfig("specular_radiance"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_diffuse_direct_light"),
                denoiserModule.getInputImageConfig("direct_radiance"));

        connect(rayTracingModule.getOutputImageConfig("diffuse_albedo_metallic"),
                denoiserModule.getInputImageConfig("diffuse_albedo"));

        connect(rayTracingModule.getOutputImageConfig("specular_albedo"),
                denoiserModule.getInputImageConfig("specular_albedo"));

        connect(rayTracingModule.getOutputImageConfig("normal_roughness"),
                denoiserModule.getInputImageConfig("normal_roughness"));

        connect(rayTracingModule.getOutputImageConfig("motion_vector"),
                denoiserModule.getInputImageConfig("motion_vector"));

        connect(rayTracingModule.getOutputImageConfig("linear_depth"),
                denoiserModule.getInputImageConfig("linear_depth"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_depth"),
                denoiserModule.getInputImageConfig("diffuseHitDepthImage"));

        connect(rayTracingModule.getOutputImageConfig("specular_hit_depth"),
                denoiserModule.getInputImageConfig("specularHitDepthImage"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_clear"),
                denoiserModule.getInputImageConfig("first_hit_clear"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_base_emission"),
                denoiserModule.getInputImageConfig("first_hit_base_emission"));

        connect(rayTracingModule.getOutputImageConfig("fog_image"),
                denoiserModule.getInputImageConfig("fog_image"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_refraction"),
                denoiserModule.getInputImageConfig("first_hit_refraction"));

        connect(denoiserModule.getOutputImageConfig("denoised_radiance"),
                temporalAccumulationModule.getInputImageConfig("color"));

        connect(rayTracingModule.getOutputImageConfig("motion_vector"),
                temporalAccumulationModule.getInputImageConfig("motion"));

        connect(rayTracingModule.getOutputImageConfig("normal_roughness"),
                temporalAccumulationModule.getInputImageConfig("normal_roughness"));

        connect(temporalAccumulationModule.getOutputImageConfig("accumulated_radiance"),
                toneMappingModule.getInputImageConfig("denoised_radiance"));

        connect(rayTracingModule.getOutputImageConfig("first_hit_depth"),
                postRenderModule.getInputImageConfig("first_hit_depth"));

        connect(toneMappingModule.getOutputImageConfig("mapped_output"),
                postRenderModule.getInputImageConfig("ldr_input"));

        connectOutput(postRenderModule.getOutputImageConfig("post_rendered"));
    }

    private static void assemblePresetByKeyInternal(String presetName) {
        if (Objects.equals(presetName, Presets.RT_DLSSRR.key)) {
            assembleDLSSRRInternal();
            return;
        }

        if (Objects.equals(presetName, Presets.RT_NRD.key)) {
            assembleNRDInternal();
            return;
        }

        if (Objects.equals(presetName, Presets.RT_NRD_FSR.key)) {
            assembleNRDFSRInternal();
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
        if (INSTANCE.mode == PipelineMode.PIPELINE) {
            return;
        }

        INSTANCE.mode = PipelineMode.PIPELINE;

        savePipeline();

        build();
    }

    public static void switchToPresetMode(String presetName) {
        String processedPresetName = processPresetName(presetName);
        if (INSTANCE.mode == PipelineMode.PRESET
                && Objects.equals(INSTANCE.activePresetName, processedPresetName)) {
            return;
        }

        List<PresetStoredModule> carryOverModules = capturePresetModules();

        INSTANCE.mode = PipelineMode.PRESET;

        // should set preset name properly
        assemblePreset(processedPresetName);

        PipelineConfigStorage storage = loadConfigStorage();
        if (storage != null && Objects.equals(storage.mode, PipelineMode.PRESET.name())
                && Objects.equals(storage.presetName, INSTANCE.activePresetName)) {
            applyPresetModuleOverrides(storage.presetModules);
        }

        applyPresetModuleOverrides(carryOverModules);

        savePipeline();
        build();
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
                && !Objects.equals(requestedPresetName, Presets.RT_NRD.key)
                && !Objects.equals(requestedPresetName, Presets.RT_NRD_FSR.key)
                && !Objects.equals(requestedPresetName, Presets.RT_NRD_XESS.key)) {
            requestedPresetName = Presets.RT_DLSSRR.key;
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
        if (module == null || module.attributeConfigs == null || storedAttributes == null) {
            return;
        }

        for (StoredAttribute storedAttribute : storedAttributes) {
            if (storedAttribute == null || storedAttribute.name == null) {
                continue;
            }

            for (int i = 0; i < module.attributeConfigs.size(); i++) {
                var attributeConfig = module.attributeConfigs.get(i);

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

    public static void savePipeline() {
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

        writeConfigStorage(storage);
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
            for (ImageConfig conf : module.outputImageConfigs) {
                if (!conf.finalOutput) {
                    continue;
                }
                if (finalOutputModuleId != null) {
                    throw new RuntimeException("Multiple final outputs detected! Only one allows.");
                }
                finalOutputModuleId = "module_" + index;
                finalOutputImageName = conf.name;
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

        for (AttributeConfig attributeConfig : module.attributeConfigs) {
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

        list.sort(Comparator.comparing(m -> m.entryName == null ? "" : m.entryName));
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
            Files.createDirectories(PIPELINE_CONFIG_PATH.getParent());
            Files.writeString(PIPELINE_CONFIG_PATH, yamlText, StandardCharsets.UTF_8);
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
            if (storage != null) {
                storage.migrateLegacyFields();
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

    private static void applyPipelineStorage(PipelineStorage pipelineStorage) {
        if (pipelineStorage == null) {
            assembleDefault();
            return;
        }

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
            for (ImageConfig conf : module.outputImageConfigs) {
                conf.finalOutput = false;
            }
        }

        Module finalModule = idToModule.get(pipelineStorage.finalOutputModuleId);
        if (finalModule == null) {
            assembleDefault();
            return;
        }

        ImageConfig finalImageConfig = finalModule.getOutputImageConfig(pipelineStorage.finalOutputImageName);
        if (finalImageConfig == null) {
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

                ImageConfig srcImageConfig = srcModule.getOutputImageConfig(storedConnection.srcImageName);
                ImageConfig dstImageConfig = dstModule.getInputImageConfig(storedConnection.dstImageName);

                if (srcImageConfig == null || dstImageConfig == null) {
                    continue;
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

        // fallback
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
        // new format
        public String mode;
        public String presetName;
        public PipelineStorage pipeline;
        public List<PresetStoredModule> presetModules;

        public List<StoredModule> modules;
        public List<StoredConnection> moduleConnections;
        public String finalOutputModuleId;
        public String finalOutputImageName;

        void migrateLegacyFields() {
            if (pipeline != null) {
                return;
            }
            if (modules == null || modules.isEmpty()) {
                return;
            }

            PipelineStorage ps = new PipelineStorage();
            ps.modules = modules;
            ps.moduleConnections = moduleConnections != null ? moduleConnections : new ArrayList<>();
            ps.finalOutputModuleId = finalOutputModuleId;
            ps.finalOutputImageName = finalOutputImageName;

            pipeline = ps;

            // default mode for legacy, pipeline
            if (mode == null) {
                mode = "PIPELINE";
            }
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
