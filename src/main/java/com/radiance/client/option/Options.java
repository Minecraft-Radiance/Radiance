package com.radiance.client.option;

import com.radiance.client.RadianceClient;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.Presets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class Options {

    public static final String OPTION_PROPERTIES = "options.properties";

    public static final String CATEGORY_GAMEPLAY = "options.video.category.gameplay";
    public static final String CATEGORY_WINDOW = "options.video.category.window";
    public static final String CATEGORY_DLSS = "options.video.category.dlss";
    public static final String CATEGORY_RAY_TRACING = "options.video.category.ray_tracing";
    public static final String CATEGORY_UPSCALER = "options.video.category.upscaler";
    public static final String CATEGORY_TERRAIN = "options.video.category.terrain";
    public static final String CATEGORY_PIPELINE = "options.video.category.pipeline";

    public static final String DLSS_MODE_PERFORMANCE_TOOLTIP = "options.video.dlss_mode.performance.tooltip";
    public static final String DLSS_MODE_BALANCED_TOOLTIP = "options.video.dlss_mode.balanced.tooltip";
    public static final String DLSS_MODE_QUALITY_TOOLTIP = "options.video.dlss_mode.quality.tooltip";
    public static final String DLSS_MODE_DLAA_TOOLTIP = "options.video.dlss_mode.dlaa.tooltip";

    public static final String DLSS_MODE_PERFORMANCE = "options.video.dlss_mode.performance";
    public static final String DLSS_MODE_BALANCED = "options.video.dlss_mode.balanced";
    public static final String DLSS_MODE_QUALITY = "options.video.dlss_mode.quality";
    public static final String DLSS_MODE_DLAA = "options.video.dlss_mode.dlaa";

    public static final String DLSS_MODE_KEY = "options.video.dlss_mode";
    public static final String QUALITY_LEVEL_KEY = "options.video.quality_level";
    public static final String UPSCALER_TYPE_KEY = "options.video.upscaler_type";
    public static final String UPSCALER_QUALITY_KEY = "options.video.upscaler_quality";
    public static final String DENOISER_MODE_KEY = "options.video.denoiser_mode";
    public static final String HDR_OUTPUT_KEY = "options.video.hdr_output";
    public static final String DLSS_FRAME_GENERATION_KEY = "options.video.dlss_frame_generation";
    public static final String RAY_BOUNCES_KEY = "options.video.ray_bounces";
    public static final String CHUNK_BUILDING_BATCH_SIZE_KEY = "options.video.chunk_building_batch_size";
    public static final String CHUNK_BUILDING_TOTAL_BATCHES_KEY = "options.video.chunk_building_total_batches";
    public static final String OUTPUT_SCALE_2X_KEY = "options.video.output_scale_2x";
    public static final String SIMPLIFIED_INDIRECT_KEY = "options.video.simplified_indirect";
    public static final String REFLEX_ENABLED_KEY = "options.video.reflex_enabled";
    public static final String REFLEX_BOOST_KEY = "options.video.reflex_boost";
    public static final String VRR_MODE_KEY = "options.video.vrr_mode";
    public static final String PIPELINE_SETUP_KEY = "options.video.pipeline_setup";

    public static final String UPSCALER_TYPE_NATIVE = "options.video.upscaler_type.native";
    public static final String UPSCALER_TYPE_FSR3 = "options.video.upscaler_type.fsr3";

    public static final String UPSCALER_QUALITY_NATIVEAA = "options.video.upscaler_quality.nativeaa";
    public static final String UPSCALER_QUALITY_QUALITY = "options.video.upscaler_quality.quality";
    public static final String UPSCALER_QUALITY_BALANCED = "options.video.upscaler_quality.balanced";
    public static final String UPSCALER_QUALITY_PERFORMANCE = "options.video.upscaler_quality.performance";
    public static final String QUALITY_LEVEL_FLUENT = "options.video.quality_level.fluent";
    public static final String QUALITY_LEVEL_PERFORMANCE = "options.video.quality_level.performance";
    public static final String QUALITY_LEVEL_BALANCED = "options.video.quality_level.balanced";
    public static final String QUALITY_LEVEL_QUALITY = "options.video.quality_level.quality";
    public static final String QUALITY_LEVEL_ULTRA = "options.video.quality_level.ultra";
    public static final String QUALITY_LEVEL_EXTREME = "options.video.quality_level.extreme";
    public static final String DENOISER_MODE_DLSS = "options.video.denoiser_mode.dlss";
    public static final String DENOISER_MODE_SVGF = "options.video.denoiser_mode.svgf";
    public static final String DENOISER_MODE_NRD = "options.video.denoiser_mode.nrd";
    public static final String DENOISER_MODE_TEMPORAL = "options.video.denoiser_mode.temporal";
    public static final String HDR_OUTPUT = "options.video.hdr_output";

    private static final String RAY_TRACING_MODULE = "render_pipeline.module.ray_tracing.name";
    private static final String DLSS_MODULE = "render_pipeline.module.dlss.name";
    private static final String FSR3_MODULE = "render_pipeline.module.fsr_upscaler.name";
    private static final String XESS_MODULE = "render_pipeline.module.xess_sr.name";
    private static final String RT_TERRAIN_MESHING_MODE =
        "render_pipeline.module.ray_tracing.attribute.terrain_meshing_mode";
    private static final String RT_GREEDY_MERGE_MAX_SPAN =
        "render_pipeline.module.ray_tracing.attribute.greedy_merge_max_span";
    private static final String RT_FAR_FIELD_GEOMETRY_MODE =
        "render_pipeline.module.ray_tracing.attribute.far_field_geometry_mode";
    private static final String RT_FAR_FIELD_START_DISTANCE_CHUNKS =
        "render_pipeline.module.ray_tracing.attribute.far_field_start_distance_chunks";
    private static final String RT_FAR_FIELD_MATERIAL_MODE =
        "render_pipeline.module.ray_tracing.attribute.far_field_material_mode";
    private static final String RT_GLASS_PATH_MODE =
        "render_pipeline.module.ray_tracing.attribute.glass_path_mode";
    private static final String RT_FOLIAGE_PATH_MODE =
        "render_pipeline.module.ray_tracing.attribute.foliage_path_mode";
    private static final String RT_DECORATION_PATH_MODE =
        "render_pipeline.module.ray_tracing.attribute.decoration_path_mode";
    private static final String RT_BLAS_INCLUSION_MODE =
        "render_pipeline.module.ray_tracing.attribute.blas_inclusion_mode";
    private static final String RT_REFLECTION_RAY_MATERIAL_MODE =
        "render_pipeline.module.ray_tracing.attribute.reflection_ray_material_mode";
    private static final String RT_DIFFUSE_GI_MODE =
        "render_pipeline.module.ray_tracing.attribute.diffuse_gi_mode";
    private static final String RT_CLOUD_VOLUME_MODE =
        "render_pipeline.module.ray_tracing.attribute.cloud_volume_mode";
    private static final String RT_TRANSPARENT_SPLIT_MODE =
        "render_pipeline.module.ray_tracing.attribute.transparent_split_mode";
    private static final String RT_USE_JITTER =
        "render_pipeline.module.ray_tracing.attribute.use_jitter";
    private static final String RT_SEPARATE_ENTITY_TERRAIN_ACCEL_STRUCTURES =
        "render_pipeline.module.ray_tracing.attribute.separate_entity_terrain_accel_structures";
    private static final String RT_TERRAIN_UPDATE_INTERVAL_FRAMES =
        "render_pipeline.module.ray_tracing.attribute.terrain_update_interval_frames";
    private static final String RT_ENTITY_UPDATE_INTERVAL_FRAMES =
        "render_pipeline.module.ray_tracing.attribute.entity_update_interval_frames";
    private static final String RT_BLOCK_ENTITY_UPDATE_INTERVAL_FRAMES =
        "render_pipeline.module.ray_tracing.attribute.block_entity_update_interval_frames";
    private static final String RT_PARTICLE_UPDATE_INTERVAL_FRAMES =
        "render_pipeline.module.ray_tracing.attribute.particle_update_interval_frames";
    private static final String RT_PARTICLE_CRIT_GLOW =
        "render_pipeline.module.ray_tracing.attribute.particle_crit_glow";
    private static final String RT_PARTICLE_DEATH_SMOKE_GLOW =
        "render_pipeline.module.ray_tracing.attribute.particle_death_smoke_glow";
    private static final String RT_PARTICLE_CRIT_GLOW_STRENGTH =
        "render_pipeline.module.ray_tracing.attribute.particle_crit_glow_strength";
    private static final String RT_PARTICLE_DEATH_SMOKE_GLOW_STRENGTH =
        "render_pipeline.module.ray_tracing.attribute.particle_death_smoke_glow_strength";
    private static final String RT_NUM_RAY_BOUNCES =
        "render_pipeline.module.ray_tracing.attribute.num_ray_bounces";
    private static final String RT_PBR_SAMPLING_MODE =
        "render_pipeline.module.ray_tracing.attribute.pbr_sampling_mode";
    private static final String RT_USE_SHARC =
        "render_pipeline.module.ray_tracing.attribute.use_sharc";
    private static final String RT_BASIC_RADIANCE =
        "render_pipeline.module.ray_tracing.attribute.basic_radiance";
    private static final String RT_DIRECT_LIGHT_STRENGTH =
        "render_pipeline.module.ray_tracing.attribute.direct_light_strength";
    private static final String RT_INDIRECT_LIGHT_STRENGTH =
        "render_pipeline.module.ray_tracing.attribute.indirect_light_strength";
    private static final String FSR3_QUALITY_MODE =
        "render_pipeline.module.fsr_upscaler.attribute.quality_mode";
    private static final String FSR3_SHARPNESS =
        "render_pipeline.module.fsr_upscaler.attribute.sharpness";
    private static final String XESS_QUALITY_MODE =
        "render_pipeline.module.xess_sr.attribute.quality_mode";
    private static final String XESS_PRE_EXPOSURE =
        "render_pipeline.module.xess_sr.attribute.pre_exposure";
    private static final String DLSS_MODE_ATTRIBUTE =
        "render_pipeline.module.dlss.attribute.mode";
    private static final String NRD_MODULE = "render_pipeline.module.nrd.name";
    private static final String NRD_ANTILAG_LUMINANCE_SIGMA_SCALE =
        "render_pipeline.module.nrd.attribute.antilag_luminance_sigma_scale";
    private static final String NRD_ANTILAG_LUMINANCE_SENSITIVITY =
        "render_pipeline.module.nrd.attribute.antilag_luminance_sensitivity";
    private static final String NRD_RESPONSIVE_ACCUMULATION_ROUGHNESS_THRESHOLD =
        "render_pipeline.module.nrd.attribute.responsive_accumulation_roughness_threshold";
    private static final String NRD_RESPONSIVE_ACCUMULATION_MIN_ACCUMULATED_FRAME_NUM =
        "render_pipeline.module.nrd.attribute.responsive_accumulation_min_accumulated_frame_num";
    private static final String NRD_MAX_ACCUMULATED_FRAME_NUM =
        "render_pipeline.module.nrd.attribute.max_accumulated_frame_num";
    private static final String NRD_MAX_FAST_ACCUMULATED_FRAME_NUM =
        "render_pipeline.module.nrd.attribute.max_fast_accumulated_frame_num";
    private static final String NRD_MAX_STABILIZED_FRAME_NUM =
        "render_pipeline.module.nrd.attribute.max_stabilized_frame_num";
    private static final String NRD_HISTORY_FIX_FRAME_NUM =
        "render_pipeline.module.nrd.attribute.history_fix_frame_num";
    private static final String NRD_HISTORY_FIX_BASE_PIXEL_STRIDE =
        "render_pipeline.module.nrd.attribute.history_fix_base_pixel_stride";
    private static final String NRD_HISTORY_FIX_ALTERNATE_PIXEL_STRIDE =
        "render_pipeline.module.nrd.attribute.history_fix_alternate_pixel_stride";
    private static final String NRD_FAST_HISTORY_CLAMPING_SIGMA_SCALE =
        "render_pipeline.module.nrd.attribute.fast_history_clamping_sigma_scale";
    private static final String NRD_DIFFUSE_PREPASS_BLUR_RADIUS =
        "render_pipeline.module.nrd.attribute.diffuse_prepass_blur_radius";
    private static final String NRD_SPECULAR_PREPASS_BLUR_RADIUS =
        "render_pipeline.module.nrd.attribute.specular_prepass_blur_radius";
    private static final String NRD_MIN_HIT_DISTANCE_WEIGHT =
        "render_pipeline.module.nrd.attribute.min_hit_distance_weight";
    private static final String NRD_MIN_BLUR_RADIUS =
        "render_pipeline.module.nrd.attribute.min_blur_radius";
    private static final String NRD_MAX_BLUR_RADIUS =
        "render_pipeline.module.nrd.attribute.max_blur_radius";
    private static final String NRD_LOBE_ANGLE_FRACTION =
        "render_pipeline.module.nrd.attribute.lobe_angle_fraction";
    private static final String NRD_ROUGHNESS_FRACTION =
        "render_pipeline.module.nrd.attribute.roughness_fraction";
    private static final String NRD_PLANE_DISTANCE_SENSITIVITY =
        "render_pipeline.module.nrd.attribute.plane_distance_sensitivity";
    private static final String NRD_SPECULAR_PROBABILITY_THRESHOLD_MIN =
        "render_pipeline.module.nrd.attribute.specular_probability_thresholds_for_mv_modification_min";
    private static final String NRD_SPECULAR_PROBABILITY_THRESHOLD_MAX =
        "render_pipeline.module.nrd.attribute.specular_probability_thresholds_for_mv_modification_max";
    private static final String NRD_FIREFLY_SUPPRESSOR_MIN_RELATIVE_SCALE =
        "render_pipeline.module.nrd.attribute.firefly_suppressor_min_relative_scale";
    private static final String NRD_MIN_MATERIAL_FOR_DIFFUSE =
        "render_pipeline.module.nrd.attribute.min_material_for_diffuse";
    private static final String NRD_MIN_MATERIAL_FOR_SPECULAR =
        "render_pipeline.module.nrd.attribute.min_material_for_specular";
    private static final String NRD_HIT_DISTANCE_RECONSTRUCTION_MODE =
        "render_pipeline.module.nrd.attribute.hit_distance_reconstruction_mode";
    private static final String NRD_ENABLE_ANTI_FIREFLY =
        "render_pipeline.module.nrd.attribute.enable_anti_firefly";
    private static final String TONE_MAPPING_MODULE = "render_pipeline.module.tone_mapping.name";
    private static final String TM_METHOD =
        "render_pipeline.module.tone_mapping.attribute.method";
    private static final String TM_MIDDLE_GREY =
        "render_pipeline.module.tone_mapping.attribute.middle_grey";
    private static final String TM_EXPOSURE_UP_SPEED =
        "render_pipeline.module.tone_mapping.attribute.exposure_up_speed";
    private static final String TM_EXPOSURE_DOWN_SPEED =
        "render_pipeline.module.tone_mapping.attribute.exposure_down_speed";
    private static final String TM_LOG2_LUMINANCE_MIN =
        "render_pipeline.module.tone_mapping.attribute.log2_luminance_min";
    private static final String TM_LOG2_LUMINANCE_MAX =
        "render_pipeline.module.tone_mapping.attribute.log2_luminance_max";
    private static final String TM_LOW_PERCENT =
        "render_pipeline.module.tone_mapping.attribute.low_percent";
    private static final String TM_HIGH_PERCENT =
        "render_pipeline.module.tone_mapping.attribute.high_percent";
    private static final String TM_MIN_EXPOSURE =
        "render_pipeline.module.tone_mapping.attribute.min_exposure";
    private static final String TM_MAX_EXPOSURE =
        "render_pipeline.module.tone_mapping.attribute.max_exposure";
    private static final String TM_ENABLE_AUTO_EXPOSURE =
        "render_pipeline.module.tone_mapping.attribute.enable_auto_exposure";
    private static final String TM_EXPOSURE_METERING_MODE =
        "render_pipeline.module.tone_mapping.attribute.exposure_metering_mode";
    private static final String TM_CENTER_METERING_PERCENT =
        "render_pipeline.module.tone_mapping.attribute.center_metering_percent";
    private static final String TM_MANUAL_EXPOSURE =
        "render_pipeline.module.tone_mapping.attribute.manual_exposure";
    private static final String TM_EXPOSURE_BIAS =
        "render_pipeline.module.tone_mapping.attribute.exposure_bias";
    private static final String TM_SATURATION =
        "render_pipeline.module.tone_mapping.attribute.saturation";
    private static final String TM_WHITE_POINT =
        "render_pipeline.module.tone_mapping.attribute.white_point";
    private static final String TM_CLAMP_OUTPUT =
        "render_pipeline.module.tone_mapping.attribute.clamp_output";
    public static int maxFps = 260;
    public static int inactivityFpsLimit = 260;
    public static boolean vsync = true;
    public static int qualityLevel = QualityLevel.BALANCED.getId();
    public static int dlssMode = 1;
    public static int upscalerType = 1;
    public static int upscalerQuality = 1;
    public static int denoiserMode = 1;
    public static boolean hdrOutput = false;
    public static boolean dlssFrameGeneration = false;
    public static boolean outputScale2x = false;
    public static boolean simplifiedIndirect = false;
    public static boolean reflexEnabled = false;
    public static boolean reflexBoost = false;
    public static boolean vrrMode = false;
    public static int rayBounces = 4;
    public static int chunkBuildingBatchSize = 14;
    public static int chunkBuildingTotalBatches = 16;
    private static final List<String> invalidOptionKeys = new ArrayList<>();

    public static void readOptions() {
        clearInvalidOptionWarnings();
        Path path = RadianceClient.radianceDir.resolve(OPTION_PROPERTIES);
        if (!Files.exists(path)) {
//            System.out.println("Generating default options...");
            overwriteConfig();
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);

            setMaxFps(parseIntProperty(props, "maxFps", maxFps), false);
            setInactivityFpsLimit(parseIntProperty(props, "inactivityFpsLimit",
                inactivityFpsLimit), false);
            setVsync(parseBooleanProperty(props, "vsync", vsync), false);
            setHdrOutput(parseBooleanProperty(props, "hdrOutput", hdrOutput), false);
            setDlssFrameGeneration(parseBooleanProperty(props, "dlssFrameGeneration",
                dlssFrameGeneration), false);
            setOutputScale2x(parseBooleanProperty(props, "outputScale2x", outputScale2x), false);
            setSimplifiedIndirect(parseBooleanProperty(props, "simplifiedIndirect",
                simplifiedIndirect), false);
            setReflexEnabled(parseBooleanProperty(props, "reflexEnabled", reflexEnabled), false);
            setReflexBoost(parseBooleanProperty(props, "reflexBoost", reflexBoost), false);
            setVrrMode(parseBooleanProperty(props, "vrrMode", vrrMode), false);
            qualityLevel = parseIntProperty(props, "qualityLevel", qualityLevel);
            dlssMode = clamp(parseIntProperty(props, "dlssMode", dlssMode), 0, 3);
            upscalerType = clamp(parseIntProperty(props, "upscalerType", upscalerType), 0, 1);
            upscalerQuality = clamp(parseIntProperty(props, "upscalerQuality", upscalerQuality), 0,
                3);
            denoiserMode = clamp(parseIntProperty(props, "denoiserMode", denoiserMode), 0, 3);
            setRayBounces(parseIntProperty(props, "rayBounces", rayBounces), false);
            setChunkBuildingBatchSize(parseIntProperty(props, "chunkBuildingBatchSize",
                chunkBuildingBatchSize), false);
            setChunkBuildingTotalBatches(
                parseIntProperty(props, "chunkBuildingTotalBatches", chunkBuildingTotalBatches),
                false);

            if (hasInvalidOptionValues()) {
                RadianceClient.LOGGER.warn(
                    "Invalid values were found in {} for keys {}. Defaults were applied and the file will be rewritten.",
                    path, invalidOptionKeys);
            }

            overwriteConfig();
//            System.out.println("Successfully read options: " + path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean hasInvalidOptionValues() {
        return !invalidOptionKeys.isEmpty();
    }

    public static List<String> getInvalidOptionKeys() {
        return List.copyOf(invalidOptionKeys);
    }

    private static void clearInvalidOptionWarnings() {
        invalidOptionKeys.clear();
    }

    private static void recordInvalidOption(String key, String rawValue) {
        if (!invalidOptionKeys.contains(key)) {
            invalidOptionKeys.add(key);
        }
        RadianceClient.LOGGER.warn(
            "Invalid options.properties value for '{}': '{}'. Falling back to the default value.",
            key, rawValue);
    }

    private static int parseIntProperty(Properties props, String key, int fallback) {
        String rawValue = props.getProperty(key);
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException e) {
            recordInvalidOption(key, rawValue);
            return fallback;
        }
    }

    private static boolean parseBooleanProperty(Properties props, String key, boolean fallback) {
        String rawValue = props.getProperty(key);
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(rawValue.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(rawValue.trim())) {
            return false;
        }
        recordInvalidOption(key, rawValue);
        return fallback;
    }

    public static void overwriteConfig() {
        Path path = RadianceClient.radianceDir.resolve(OPTION_PROPERTIES);
        Properties props = new Properties();
        props.setProperty("maxFps", String.valueOf(maxFps));
        props.setProperty("inactivityFpsLimit", String.valueOf(inactivityFpsLimit));
        props.setProperty("vsync", String.valueOf(vsync));
        props.setProperty("hdrOutput", String.valueOf(hdrOutput));
        props.setProperty("qualityLevel", String.valueOf(qualityLevel));
        props.setProperty("dlssMode", String.valueOf(dlssMode));
        props.setProperty("upscalerType", String.valueOf(upscalerType));
        props.setProperty("upscalerQuality", String.valueOf(upscalerQuality));
        props.setProperty("denoiserMode", String.valueOf(denoiserMode));
        props.setProperty("rayBounces", String.valueOf(rayBounces));
        props.setProperty("chunkBuildingBatchSize", String.valueOf(chunkBuildingBatchSize));
        props.setProperty("chunkBuildingTotalBatches", String.valueOf(chunkBuildingTotalBatches));
        props.setProperty("dlssFrameGeneration", String.valueOf(dlssFrameGeneration));
        props.setProperty("outputScale2x", String.valueOf(outputScale2x));
        props.setProperty("simplifiedIndirect", String.valueOf(simplifiedIndirect));
        props.setProperty("reflexEnabled", String.valueOf(reflexEnabled));
        props.setProperty("reflexBoost", String.valueOf(reflexBoost));
        props.setProperty("vrrMode", String.valueOf(vrrMode));

        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, "Options");
//            System.out.println("Options written to: " + path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public native static void nativeSetMaxFps(int maxFps, boolean write);

    public static void setMaxFps(int maxFps, boolean write) {
        Options.maxFps = maxFps;
        nativeSetMaxFps(maxFps, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetInactivityFpsLimit(int inactivityFpsLimit, boolean write);

    public static void setInactivityFpsLimit(int inactivityFpsLimit, boolean write) {
        Options.inactivityFpsLimit = inactivityFpsLimit;
        nativeSetInactivityFpsLimit(inactivityFpsLimit, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetVsync(boolean vsync, boolean write);

    public static void setVsync(boolean vsync, boolean write) {
        Options.vsync = vsync;
        nativeSetVsync(vsync, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetChunkBuildingBatchSize(int chunkBuildingBatchSize,
        boolean write);

    public static void setChunkBuildingBatchSize(int chunkBuildingBatchSize, boolean write) {
        Options.chunkBuildingBatchSize = chunkBuildingBatchSize;
        nativeSetChunkBuildingBatchSize(chunkBuildingBatchSize, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetChunkBuildingTotalBatches(int chunkBuildingTotalBatches,
        boolean write);

    public static void setChunkBuildingTotalBatches(int chunkBuildingTotalBatches, boolean write) {
        Options.chunkBuildingTotalBatches = chunkBuildingTotalBatches;
        nativeSetChunkBuildingTotalBatches(chunkBuildingTotalBatches, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetHdrOutput(boolean hdrOutput, boolean write);

    public static void setHdrOutput(boolean hdrOutput, boolean write) {
        Options.hdrOutput = hdrOutput;
        nativeSetHdrOutput(hdrOutput, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetDlssFrameGeneration(boolean dlssFrameGeneration, boolean write);
    public native static boolean nativeHasDlssFrameGenerationAvailable();
    public native static void nativeSetOutputScale2x(boolean enabled, boolean write);
    public native static void nativeSetSimplifiedIndirect(boolean enabled, boolean write);
    public native static void nativeSetReflexEnabled(boolean enabled, boolean write);
    public native static void nativeSetReflexBoost(boolean enabled, boolean write);
    public native static boolean nativeIsReflexSupported();
    public native static void nativeSetVrrMode(boolean enabled, boolean write);
    public native static int nativeGetDisplayRefreshRate();

    public static void setDlssFrameGeneration(boolean dlssFrameGeneration, boolean write) {
        if (write && dlssFrameGeneration && !nativeHasDlssFrameGenerationAvailable()) {
            RadianceClient.LOGGER.warn(
                "DLSS Frame Generation was requested, but the current system/runtime does not expose it. Keeping the option disabled.");
            dlssFrameGeneration = false;
        }
        Options.dlssFrameGeneration = dlssFrameGeneration;
        nativeSetDlssFrameGeneration(dlssFrameGeneration, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setOutputScale2x(boolean enabled, boolean write) {
        outputScale2x = enabled;
        nativeSetOutputScale2x(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setSimplifiedIndirect(boolean enabled, boolean write) {
        simplifiedIndirect = enabled;
        nativeSetSimplifiedIndirect(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static boolean isReflexSupported() {
        try {
            return nativeIsReflexSupported();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static void setReflexEnabled(boolean enabled, boolean write) {
        if (write && enabled && !isReflexSupported()) {
            RadianceClient.LOGGER.warn(
                "NVIDIA Reflex was requested, but the current runtime does not expose Streamline Reflex support. Keeping the option disabled.");
            enabled = false;
        }
        reflexEnabled = enabled;
        nativeSetReflexEnabled(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setReflexBoost(boolean enabled, boolean write) {
        reflexBoost = enabled;
        nativeSetReflexBoost(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setVrrMode(boolean enabled, boolean write) {
        vrrMode = enabled;
        nativeSetVrrMode(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static int getDisplayRefreshRate() {
        try {
            return nativeGetDisplayRefreshRate();
        } catch (UnsatisfiedLinkError e) {
            return 0;
        }
    }

    public native static void nativeSetRayBounces(int rayBounces, boolean write);

    public static void setRayBounces(int rayBounces, boolean write) {
        Options.rayBounces = rayBounces;
        nativeSetRayBounces(rayBounces, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setDlssMode(int dlssMode, boolean write) {
        Options.dlssMode = Math.max(0, Math.min(3, dlssMode));
        String mappedMode = switch (Options.dlssMode) {
            case 0 -> "render_pipeline.module.dlss.attribute.mode.performance";
            case 1 -> "render_pipeline.module.dlss.attribute.mode.balanced";
            case 2 -> "render_pipeline.module.dlss.attribute.mode.quality";
            default -> "render_pipeline.module.dlss.attribute.mode.dlaa";
        };
        setAttr(DLSS_MODULE, DLSS_MODE_ATTRIBUTE, mappedMode);
        if (write) {
            overwriteConfig();
        }
    }

    public static void applyQualityProfile(boolean rebuildPipeline) {
        applyQualityProfile(QualityLevel.fromId(qualityLevel), rebuildPipeline, false);
    }

    public static void setQualityLevel(QualityLevel qualityLevel, boolean write) {
        applyQualityProfile(qualityLevel, true, write);
    }

    private static void applyQualityProfile(QualityLevel level, boolean rebuildPipeline,
        boolean writeOptions) {
        QualityLevel resolvedLevel = level == null ? QualityLevel.BALANCED : level;
        Options.qualityLevel = resolvedLevel.getId();

        boolean pipelineChanged = false;
        if (Pipeline.INSTANCE.getModuleEntries() != null) {
            boolean shouldSwitchPreset = writeOptions || Pipeline.INSTANCE.getModules().isEmpty();
            if (shouldSwitchPreset) {
                pipelineChanged |= preparePresetForQuality(resolvedLevel);
            }
            if (!Pipeline.INSTANCE.getModules().isEmpty()) {
                pipelineChanged |= applyPipelineQualityProfile(resolvedLevel);
            }
        }

        if (writeOptions) {
            overwriteConfig();
        }
        if (pipelineChanged && rebuildPipeline) {
            Pipeline.savePipeline();
            Pipeline.build();
        }
    }

    private static boolean applyPipelineQualityProfile(QualityLevel level) {
        return switch (level) {
            case FLUENT -> applyFluentQualityProfile();
            case PERFORMANCE -> applyPerformanceQualityProfile();
            case BALANCED -> applyBalancedQualityProfile();
            case HIGH -> applyHighQualityProfile();
            case ULTRA -> applyUltraQualityProfile();
            case EXTREME -> applyExtremeQualityProfile();
        };
    }

    private static boolean preparePresetForQuality(QualityLevel level) {
        String presetName = selectPresetForQuality(level);
        if (presetName == null) {
            return false;
        }

        String activePreset = Pipeline.processPresetName(Pipeline.getActivePreset());
        if (Pipeline.getPipelineMode() == Pipeline.PipelineMode.PRESET
            && Objects.equals(activePreset, presetName)
            && !Pipeline.INSTANCE.getModules().isEmpty()) {
            return false;
        }

        Pipeline.preparePresetMode(presetName);
        return true;
    }

    private static String selectPresetForQuality(QualityLevel level) {
        return switch (level) {
            case HIGH, ULTRA, EXTREME -> firstAvailablePreset(
                Presets.RT_DLSSRR.key,
                Presets.RT_NRD_XESS.key,
                Presets.RT_NRD_FSR.key,
                Presets.RT_NRD.key);
            default -> firstAvailablePreset(
                Presets.RT_NRD_FSR.key,
                Presets.RT_NRD_XESS.key,
                Presets.RT_NRD.key,
                Presets.RT_DLSSRR.key);
        };
    }

    public static String getPreferredPresetForCurrentQuality() {
        return selectPresetForQuality(QualityLevel.fromId(qualityLevel));
    }

    public static boolean shouldUseDlssPresetForCurrentQuality() {
        return Objects.equals(getPreferredPresetForCurrentQuality(), Presets.RT_DLSSRR.key);
    }

    private static String firstAvailablePreset(String... presetNames) {
        for (String presetName : presetNames) {
            if (Pipeline.isPresetAvailable(presetName)) {
                return presetName;
            }
        }
        return null;
    }

    private static boolean applyFluentQualityProfile() {
        setRayBounces(2, false);
        setChunkBuildingBatchSize(18, false);
        setChunkBuildingTotalBatches(20, false);
        dlssMode = 1;
        upscalerQuality = 3;
        denoiserMode = 2;

        boolean changed = false;
        changed |= setAttr(RAY_TRACING_MODULE, RT_TERRAIN_MESHING_MODE,
            "render_pipeline.module.ray_tracing.attribute.terrain_meshing_mode.coplanar_merge");
        changed |= setAttr(RAY_TRACING_MODULE, RT_GREEDY_MERGE_MAX_SPAN, "24");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_GEOMETRY_MODE,
            "render_pipeline.module.ray_tracing.attribute.far_field_geometry_mode.simplified_shell");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_START_DISTANCE_CHUNKS, "32");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_MATERIAL_MODE,
            "render_pipeline.module.ray_tracing.attribute.far_field_material_mode.flat_surface");
        changed |= setAttr(RAY_TRACING_MODULE, RT_GLASS_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FOLIAGE_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DECORATION_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BLAS_INCLUSION_MODE,
            "render_pipeline.module.ray_tracing.attribute.blas_inclusion_mode.opaque_only");
        changed |= setAttr(RAY_TRACING_MODULE, RT_REFLECTION_RAY_MATERIAL_MODE,
            "render_pipeline.module.ray_tracing.attribute.reflection_ray_material_mode.water_glass_metal");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DIFFUSE_GI_MODE,
            "render_pipeline.module.ray_tracing.attribute.diffuse_gi_mode.low_cost_hybrid");
        changed |= setAttr(RAY_TRACING_MODULE, RT_CLOUD_VOLUME_MODE,
            "render_pipeline.module.ray_tracing.attribute.cloud_volume_mode.native");
        changed |= setAttr(RAY_TRACING_MODULE, RT_TERRAIN_UPDATE_INTERVAL_FRAMES, "4");
        changed |= setAttr(RAY_TRACING_MODULE, RT_ENTITY_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BLOCK_ENTITY_UPDATE_INTERVAL_FRAMES, "2");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_UPDATE_INTERVAL_FRAMES, "3");
        changed |= setAttr(RAY_TRACING_MODULE, RT_TRANSPARENT_SPLIT_MODE,
            "render_pipeline.module.ray_tracing.attribute.transparent_split_mode.deterministic");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_CRIT_GLOW, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_DEATH_SMOKE_GLOW, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_CRIT_GLOW_STRENGTH, "0.42");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_DEATH_SMOKE_GLOW_STRENGTH, "0.20");
        changed |= setAttr(RAY_TRACING_MODULE, RT_NUM_RAY_BOUNCES, "2");
        changed |= setAttr(RAY_TRACING_MODULE, RT_USE_JITTER, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PBR_SAMPLING_MODE,
            "render_pipeline.module.ray_tracing.attribute.pbr_sampling.bilinear");
        changed |= setAttr(RAY_TRACING_MODULE, RT_USE_SHARC, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_SEPARATE_ENTITY_TERRAIN_ACCEL_STRUCTURES,
            "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BASIC_RADIANCE, "5.8");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DIRECT_LIGHT_STRENGTH, "1.0");
        changed |= setAttr(RAY_TRACING_MODULE, RT_INDIRECT_LIGHT_STRENGTH, "16.5");
        changed |= setAttr(NRD_MODULE, NRD_ANTILAG_LUMINANCE_SIGMA_SCALE, "4.6");
        changed |= setAttr(NRD_MODULE, NRD_ANTILAG_LUMINANCE_SENSITIVITY, "3.6");
        changed |= setAttr(NRD_MODULE, NRD_RESPONSIVE_ACCUMULATION_ROUGHNESS_THRESHOLD, "0.16");
        changed |= setAttr(NRD_MODULE, NRD_RESPONSIVE_ACCUMULATION_MIN_ACCUMULATED_FRAME_NUM, "1");
        changed |= setAttr(NRD_MODULE, NRD_MAX_ACCUMULATED_FRAME_NUM, "24");
        changed |= setAttr(NRD_MODULE, NRD_MAX_FAST_ACCUMULATED_FRAME_NUM, "1");
        changed |= setAttr(NRD_MODULE, NRD_MAX_STABILIZED_FRAME_NUM, "28");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_FRAME_NUM, "1");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_BASE_PIXEL_STRIDE, "10");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_ALTERNATE_PIXEL_STRIDE, "12");
        changed |= setAttr(NRD_MODULE, NRD_FAST_HISTORY_CLAMPING_SIGMA_SCALE, "1.35");
        changed |= setAttr(NRD_MODULE, NRD_DIFFUSE_PREPASS_BLUR_RADIUS, "18.0");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PREPASS_BLUR_RADIUS, "26.0");
        changed |= setAttr(NRD_MODULE, NRD_MIN_HIT_DISTANCE_WEIGHT, "0.12");
        changed |= setAttr(NRD_MODULE, NRD_MIN_BLUR_RADIUS, "1.2");
        changed |= setAttr(NRD_MODULE, NRD_MAX_BLUR_RADIUS, "48.0");
        changed |= setAttr(NRD_MODULE, NRD_LOBE_ANGLE_FRACTION, "0.19");
        changed |= setAttr(NRD_MODULE, NRD_ROUGHNESS_FRACTION, "0.18");
        changed |= setAttr(NRD_MODULE, NRD_PLANE_DISTANCE_SENSITIVITY, "0.040");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PROBABILITY_THRESHOLD_MIN, "0.44");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PROBABILITY_THRESHOLD_MAX, "0.82");
        changed |= setAttr(NRD_MODULE, NRD_FIREFLY_SUPPRESSOR_MIN_RELATIVE_SCALE, "2.4");
        changed |= setAttr(NRD_MODULE, NRD_MIN_MATERIAL_FOR_DIFFUSE, "5.0");
        changed |= setAttr(NRD_MODULE, NRD_MIN_MATERIAL_FOR_SPECULAR, "5.0");
        changed |= setAttr(NRD_MODULE, NRD_HIT_DISTANCE_RECONSTRUCTION_MODE, "5x5");
        changed |= setAttr(NRD_MODULE, NRD_ENABLE_ANTI_FIREFLY, "render_pipeline.true");
        changed |= setAttr(FSR3_MODULE, FSR3_QUALITY_MODE,
            "render_pipeline.module.fsr_upscaler.attribute.quality_mode.performance");
        changed |= setAttr(FSR3_MODULE, FSR3_SHARPNESS, "0.60");
        changed |= setAttr(XESS_MODULE, XESS_QUALITY_MODE,
            "render_pipeline.module.xess_sr.attribute.quality_mode.performance");
        changed |= setAttr(XESS_MODULE, XESS_PRE_EXPOSURE, "1.00");
        changed |= setAttr(DLSS_MODULE, DLSS_MODE_ATTRIBUTE,
            "render_pipeline.module.dlss.attribute.mode.performance");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_METHOD,
            "render_pipeline.module.tone_mapping.attribute.method.pbr_neutral");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MIDDLE_GREY, "0.20");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_UP_SPEED, "9.5");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_DOWN_SPEED, "8.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOG2_LUMINANCE_MIN, "-11.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOG2_LUMINANCE_MAX, "5.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOW_PERCENT, "0.010");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_HIGH_PERCENT, "0.985");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MIN_EXPOSURE, "0.03");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MAX_EXPOSURE, "1.7");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_ENABLE_AUTO_EXPOSURE, "render_pipeline.true");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_METERING_MODE,
            "render_pipeline.module.tone_mapping.attribute.exposure_metering_mode.global");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_CENTER_METERING_PERCENT, "26.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MANUAL_EXPOSURE, "1.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_BIAS, "-0.10");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_SATURATION, "0.97");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_WHITE_POINT, "24.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_CLAMP_OUTPUT, "render_pipeline.true");
        return changed;
    }

    private static boolean applyPerformanceQualityProfile() {
        setRayBounces(3, false);
        setChunkBuildingBatchSize(18, false);
        setChunkBuildingTotalBatches(20, false);
        dlssMode = 0;
        upscalerQuality = 2;
        denoiserMode = 2;

        boolean changed = false;
        changed |= setAttr(RAY_TRACING_MODULE, RT_TERRAIN_MESHING_MODE,
            "render_pipeline.module.ray_tracing.attribute.terrain_meshing_mode.coplanar_merge");
        changed |= setAttr(RAY_TRACING_MODULE, RT_GREEDY_MERGE_MAX_SPAN, "28");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_GEOMETRY_MODE,
            "render_pipeline.module.ray_tracing.attribute.far_field_geometry_mode.simplified_shell");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_START_DISTANCE_CHUNKS, "40");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_MATERIAL_MODE,
            "render_pipeline.module.ray_tracing.attribute.far_field_material_mode.flat_surface");
        changed |= setAttr(RAY_TRACING_MODULE, RT_GLASS_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FOLIAGE_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DECORATION_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BLAS_INCLUSION_MODE,
            "render_pipeline.module.ray_tracing.attribute.blas_inclusion_mode.opaque_and_shadow");
        changed |= setAttr(RAY_TRACING_MODULE, RT_REFLECTION_RAY_MATERIAL_MODE,
            "render_pipeline.module.ray_tracing.attribute.reflection_ray_material_mode.water_glass_metal");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DIFFUSE_GI_MODE,
            "render_pipeline.module.ray_tracing.attribute.diffuse_gi_mode.low_cost_hybrid");
        changed |= setAttr(RAY_TRACING_MODULE, RT_CLOUD_VOLUME_MODE,
            "render_pipeline.module.ray_tracing.attribute.cloud_volume_mode.efficient_volume");
        changed |= setAttr(RAY_TRACING_MODULE, RT_TERRAIN_UPDATE_INTERVAL_FRAMES, "3");
        changed |= setAttr(RAY_TRACING_MODULE, RT_ENTITY_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BLOCK_ENTITY_UPDATE_INTERVAL_FRAMES, "2");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_UPDATE_INTERVAL_FRAMES, "2");
        changed |= setAttr(RAY_TRACING_MODULE, RT_TRANSPARENT_SPLIT_MODE,
            "render_pipeline.module.ray_tracing.attribute.transparent_split_mode.deterministic");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_CRIT_GLOW, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_DEATH_SMOKE_GLOW, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_CRIT_GLOW_STRENGTH, "0.50");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_DEATH_SMOKE_GLOW_STRENGTH, "0.26");
        changed |= setAttr(RAY_TRACING_MODULE, RT_NUM_RAY_BOUNCES, "3");
        changed |= setAttr(RAY_TRACING_MODULE, RT_USE_JITTER, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PBR_SAMPLING_MODE,
            "render_pipeline.module.ray_tracing.attribute.pbr_sampling.bilinear");
        changed |= setAttr(RAY_TRACING_MODULE, RT_USE_SHARC, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_SEPARATE_ENTITY_TERRAIN_ACCEL_STRUCTURES,
            "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BASIC_RADIANCE, "5.55");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DIRECT_LIGHT_STRENGTH, "1.0");
        changed |= setAttr(RAY_TRACING_MODULE, RT_INDIRECT_LIGHT_STRENGTH, "17.0");
        changed |= setAttr(NRD_MODULE, NRD_ANTILAG_LUMINANCE_SIGMA_SCALE, "4.2");
        changed |= setAttr(NRD_MODULE, NRD_ANTILAG_LUMINANCE_SENSITIVITY, "3.3");
        changed |= setAttr(NRD_MODULE, NRD_RESPONSIVE_ACCUMULATION_ROUGHNESS_THRESHOLD, "0.12");
        changed |= setAttr(NRD_MODULE, NRD_RESPONSIVE_ACCUMULATION_MIN_ACCUMULATED_FRAME_NUM, "1");
        changed |= setAttr(NRD_MODULE, NRD_MAX_ACCUMULATED_FRAME_NUM, "32");
        changed |= setAttr(NRD_MODULE, NRD_MAX_FAST_ACCUMULATED_FRAME_NUM, "1");
        changed |= setAttr(NRD_MODULE, NRD_MAX_STABILIZED_FRAME_NUM, "36");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_FRAME_NUM, "1");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_BASE_PIXEL_STRIDE, "12");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_ALTERNATE_PIXEL_STRIDE, "12");
        changed |= setAttr(NRD_MODULE, NRD_FAST_HISTORY_CLAMPING_SIGMA_SCALE, "1.40");
        changed |= setAttr(NRD_MODULE, NRD_DIFFUSE_PREPASS_BLUR_RADIUS, "20.0");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PREPASS_BLUR_RADIUS, "30.0");
        changed |= setAttr(NRD_MODULE, NRD_MIN_HIT_DISTANCE_WEIGHT, "0.11");
        changed |= setAttr(NRD_MODULE, NRD_MIN_BLUR_RADIUS, "1.1");
        changed |= setAttr(NRD_MODULE, NRD_MAX_BLUR_RADIUS, "60.0");
        changed |= setAttr(NRD_MODULE, NRD_LOBE_ANGLE_FRACTION, "0.18");
        changed |= setAttr(NRD_MODULE, NRD_ROUGHNESS_FRACTION, "0.17");
        changed |= setAttr(NRD_MODULE, NRD_PLANE_DISTANCE_SENSITIVITY, "0.034");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PROBABILITY_THRESHOLD_MIN, "0.46");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PROBABILITY_THRESHOLD_MAX, "0.84");
        changed |= setAttr(NRD_MODULE, NRD_FIREFLY_SUPPRESSOR_MIN_RELATIVE_SCALE, "2.3");
        changed |= setAttr(NRD_MODULE, NRD_MIN_MATERIAL_FOR_DIFFUSE, "4.5");
        changed |= setAttr(NRD_MODULE, NRD_MIN_MATERIAL_FOR_SPECULAR, "4.5");
        changed |= setAttr(NRD_MODULE, NRD_HIT_DISTANCE_RECONSTRUCTION_MODE, "5x5");
        changed |= setAttr(NRD_MODULE, NRD_ENABLE_ANTI_FIREFLY, "render_pipeline.true");
        changed |= setAttr(FSR3_MODULE, FSR3_QUALITY_MODE,
            "render_pipeline.module.fsr_upscaler.attribute.quality_mode.balanced");
        changed |= setAttr(FSR3_MODULE, FSR3_SHARPNESS, "0.66");
        changed |= setAttr(XESS_MODULE, XESS_QUALITY_MODE,
            "render_pipeline.module.xess_sr.attribute.quality_mode.balanced");
        changed |= setAttr(XESS_MODULE, XESS_PRE_EXPOSURE, "1.00");
        changed |= setAttr(DLSS_MODULE, DLSS_MODE_ATTRIBUTE,
            "render_pipeline.module.dlss.attribute.mode.performance");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_METHOD,
            "render_pipeline.module.tone_mapping.attribute.method.pbr_neutral");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MIDDLE_GREY, "0.19");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_UP_SPEED, "9.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_DOWN_SPEED, "8.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOG2_LUMINANCE_MIN, "-11.5");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOG2_LUMINANCE_MAX, "5.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOW_PERCENT, "0.008");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_HIGH_PERCENT, "0.988");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MIN_EXPOSURE, "0.025");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MAX_EXPOSURE, "1.9");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_ENABLE_AUTO_EXPOSURE, "render_pipeline.true");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_METERING_MODE,
            "render_pipeline.module.tone_mapping.attribute.exposure_metering_mode.global");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_CENTER_METERING_PERCENT, "24.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MANUAL_EXPOSURE, "1.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_BIAS, "-0.05");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_SATURATION, "0.99");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_WHITE_POINT, "26.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_CLAMP_OUTPUT, "render_pipeline.true");
        return changed;
    }

    private static boolean applyBalancedQualityProfile() {
        setRayBounces(4, false);
        setChunkBuildingBatchSize(16, false);
        setChunkBuildingTotalBatches(18, false);
        dlssMode = 1;
        upscalerQuality = 1;
        denoiserMode = 2;

        boolean changed = false;
        changed |= setAttr(RAY_TRACING_MODULE, RT_TERRAIN_MESHING_MODE,
            "render_pipeline.module.ray_tracing.attribute.terrain_meshing_mode.coplanar_merge");
        changed |= setAttr(RAY_TRACING_MODULE, RT_GREEDY_MERGE_MAX_SPAN, "30");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_GEOMETRY_MODE,
            "render_pipeline.module.ray_tracing.attribute.far_field_geometry_mode.exact_chunks");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_START_DISTANCE_CHUNKS, "48");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_MATERIAL_MODE,
            "render_pipeline.module.ray_tracing.attribute.far_field_material_mode.full_pbr");
        changed |= setAttr(RAY_TRACING_MODULE, RT_GLASS_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FOLIAGE_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DECORATION_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BLAS_INCLUSION_MODE,
            "render_pipeline.module.ray_tracing.attribute.blas_inclusion_mode.opaque_and_shadow");
        changed |= setAttr(RAY_TRACING_MODULE, RT_REFLECTION_RAY_MATERIAL_MODE,
            "render_pipeline.module.ray_tracing.attribute.reflection_ray_material_mode.water_glass_metal");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DIFFUSE_GI_MODE,
            "render_pipeline.module.ray_tracing.attribute.diffuse_gi_mode.low_cost_hybrid");
        changed |= setAttr(RAY_TRACING_MODULE, RT_CLOUD_VOLUME_MODE,
            "render_pipeline.module.ray_tracing.attribute.cloud_volume_mode.efficient_volume");
        changed |= setAttr(RAY_TRACING_MODULE, RT_TERRAIN_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_ENTITY_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BLOCK_ENTITY_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_TRANSPARENT_SPLIT_MODE,
            "render_pipeline.module.ray_tracing.attribute.transparent_split_mode.deterministic");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_CRIT_GLOW, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_DEATH_SMOKE_GLOW, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_CRIT_GLOW_STRENGTH, "0.60");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_DEATH_SMOKE_GLOW_STRENGTH, "0.34");
        changed |= setAttr(RAY_TRACING_MODULE, RT_NUM_RAY_BOUNCES, "4");
        changed |= setAttr(RAY_TRACING_MODULE, RT_USE_JITTER, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PBR_SAMPLING_MODE,
            "render_pipeline.module.ray_tracing.attribute.pbr_sampling.bilinear");
        changed |= setAttr(RAY_TRACING_MODULE, RT_USE_SHARC, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_SEPARATE_ENTITY_TERRAIN_ACCEL_STRUCTURES,
            "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BASIC_RADIANCE, "5.2");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DIRECT_LIGHT_STRENGTH, "1.0");
        changed |= setAttr(RAY_TRACING_MODULE, RT_INDIRECT_LIGHT_STRENGTH, "17.6");
        changed |= setAttr(NRD_MODULE, NRD_ANTILAG_LUMINANCE_SIGMA_SCALE, "3.9");
        changed |= setAttr(NRD_MODULE, NRD_ANTILAG_LUMINANCE_SENSITIVITY, "3.1");
        changed |= setAttr(NRD_MODULE, NRD_RESPONSIVE_ACCUMULATION_ROUGHNESS_THRESHOLD, "0.06");
        changed |= setAttr(NRD_MODULE, NRD_RESPONSIVE_ACCUMULATION_MIN_ACCUMULATED_FRAME_NUM, "2");
        changed |= setAttr(NRD_MODULE, NRD_MAX_ACCUMULATED_FRAME_NUM, "64");
        changed |= setAttr(NRD_MODULE, NRD_MAX_FAST_ACCUMULATED_FRAME_NUM, "4");
        changed |= setAttr(NRD_MODULE, NRD_MAX_STABILIZED_FRAME_NUM, "68");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_FRAME_NUM, "2");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_BASE_PIXEL_STRIDE, "14");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_ALTERNATE_PIXEL_STRIDE, "14");
        changed |= setAttr(NRD_MODULE, NRD_FAST_HISTORY_CLAMPING_SIGMA_SCALE, "1.7");
        changed |= setAttr(NRD_MODULE, NRD_DIFFUSE_PREPASS_BLUR_RADIUS, "26.0");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PREPASS_BLUR_RADIUS, "42.0");
        changed |= setAttr(NRD_MODULE, NRD_MIN_HIT_DISTANCE_WEIGHT, "0.10");
        changed |= setAttr(NRD_MODULE, NRD_MIN_BLUR_RADIUS, "1.0");
        changed |= setAttr(NRD_MODULE, NRD_MAX_BLUR_RADIUS, "92.0");
        changed |= setAttr(NRD_MODULE, NRD_LOBE_ANGLE_FRACTION, "0.17");
        changed |= setAttr(NRD_MODULE, NRD_ROUGHNESS_FRACTION, "0.16");
        changed |= setAttr(NRD_MODULE, NRD_PLANE_DISTANCE_SENSITIVITY, "0.023");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PROBABILITY_THRESHOLD_MIN, "0.48");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PROBABILITY_THRESHOLD_MAX, "0.86");
        changed |= setAttr(NRD_MODULE, NRD_FIREFLY_SUPPRESSOR_MIN_RELATIVE_SCALE, "2.2");
        changed |= setAttr(NRD_MODULE, NRD_MIN_MATERIAL_FOR_DIFFUSE, "4.0");
        changed |= setAttr(NRD_MODULE, NRD_MIN_MATERIAL_FOR_SPECULAR, "4.0");
        changed |= setAttr(NRD_MODULE, NRD_HIT_DISTANCE_RECONSTRUCTION_MODE, "5x5");
        changed |= setAttr(NRD_MODULE, NRD_ENABLE_ANTI_FIREFLY, "render_pipeline.true");
        changed |= setAttr(FSR3_MODULE, FSR3_QUALITY_MODE,
            "render_pipeline.module.fsr_upscaler.attribute.quality_mode.quality");
        changed |= setAttr(FSR3_MODULE, FSR3_SHARPNESS, "0.74");
        changed |= setAttr(XESS_MODULE, XESS_QUALITY_MODE,
            "render_pipeline.module.xess_sr.attribute.quality_mode.quality");
        changed |= setAttr(XESS_MODULE, XESS_PRE_EXPOSURE, "1.02");
        changed |= setAttr(DLSS_MODULE, DLSS_MODE_ATTRIBUTE,
            "render_pipeline.module.dlss.attribute.mode.balanced");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_METHOD,
            "render_pipeline.module.tone_mapping.attribute.method.pbr_neutral");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MIDDLE_GREY, "0.18");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_UP_SPEED, "8.5");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_DOWN_SPEED, "7.5");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOG2_LUMINANCE_MIN, "-12.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOG2_LUMINANCE_MAX, "4.5");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOW_PERCENT, "0.006");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_HIGH_PERCENT, "0.990");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MIN_EXPOSURE, "0.02");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MAX_EXPOSURE, "2.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_ENABLE_AUTO_EXPOSURE, "render_pipeline.true");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_METERING_MODE,
            "render_pipeline.module.tone_mapping.attribute.exposure_metering_mode.center");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_CENTER_METERING_PERCENT, "22.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MANUAL_EXPOSURE, "1.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_BIAS, "0.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_SATURATION, "1.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_WHITE_POINT, "29.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_CLAMP_OUTPUT, "render_pipeline.true");
        return changed;
    }

    private static boolean applyHighQualityProfile() {
        setRayBounces(6, false);
        setChunkBuildingBatchSize(14, false);
        setChunkBuildingTotalBatches(16, false);
        dlssMode = 2;
        upscalerQuality = 0;
        denoiserMode = 2;

        boolean changed = false;
        changed |= setAttr(RAY_TRACING_MODULE, RT_TERRAIN_MESHING_MODE,
            "render_pipeline.module.ray_tracing.attribute.terrain_meshing_mode.coplanar_merge");
        changed |= setAttr(RAY_TRACING_MODULE, RT_GREEDY_MERGE_MAX_SPAN, "34");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_GEOMETRY_MODE,
            "render_pipeline.module.ray_tracing.attribute.far_field_geometry_mode.exact_chunks");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_START_DISTANCE_CHUNKS, "64");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_MATERIAL_MODE,
            "render_pipeline.module.ray_tracing.attribute.far_field_material_mode.full_pbr");
        changed |= setAttr(RAY_TRACING_MODULE, RT_GLASS_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FOLIAGE_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DECORATION_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BLAS_INCLUSION_MODE,
            "render_pipeline.module.ray_tracing.attribute.blas_inclusion_mode.opaque_and_shadow");
        changed |= setAttr(RAY_TRACING_MODULE, RT_REFLECTION_RAY_MATERIAL_MODE,
            "render_pipeline.module.ray_tracing.attribute.reflection_ray_material_mode.all_materials");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DIFFUSE_GI_MODE,
            "render_pipeline.module.ray_tracing.attribute.diffuse_gi_mode.full_ray_tracing");
        changed |= setAttr(RAY_TRACING_MODULE, RT_CLOUD_VOLUME_MODE,
            "render_pipeline.module.ray_tracing.attribute.cloud_volume_mode.realistic_volume");
        changed |= setAttr(RAY_TRACING_MODULE, RT_TERRAIN_UPDATE_INTERVAL_FRAMES, "2");
        changed |= setAttr(RAY_TRACING_MODULE, RT_ENTITY_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BLOCK_ENTITY_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_TRANSPARENT_SPLIT_MODE,
            "render_pipeline.module.ray_tracing.attribute.transparent_split_mode.stochastic");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_CRIT_GLOW, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_DEATH_SMOKE_GLOW, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_CRIT_GLOW_STRENGTH, "0.72");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_DEATH_SMOKE_GLOW_STRENGTH, "0.44");
        changed |= setAttr(RAY_TRACING_MODULE, RT_NUM_RAY_BOUNCES, "6");
        changed |= setAttr(RAY_TRACING_MODULE, RT_USE_JITTER, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PBR_SAMPLING_MODE,
            "render_pipeline.module.ray_tracing.attribute.pbr_sampling.bilinear");
        changed |= setAttr(RAY_TRACING_MODULE, RT_USE_SHARC, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_SEPARATE_ENTITY_TERRAIN_ACCEL_STRUCTURES,
            "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BASIC_RADIANCE, "4.95");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DIRECT_LIGHT_STRENGTH, "1.0");
        changed |= setAttr(RAY_TRACING_MODULE, RT_INDIRECT_LIGHT_STRENGTH, "18.6");
        changed |= setAttr(NRD_MODULE, NRD_ANTILAG_LUMINANCE_SIGMA_SCALE, "3.6");
        changed |= setAttr(NRD_MODULE, NRD_ANTILAG_LUMINANCE_SENSITIVITY, "2.8");
        changed |= setAttr(NRD_MODULE, NRD_RESPONSIVE_ACCUMULATION_ROUGHNESS_THRESHOLD, "0.04");
        changed |= setAttr(NRD_MODULE, NRD_RESPONSIVE_ACCUMULATION_MIN_ACCUMULATED_FRAME_NUM, "3");
        changed |= setAttr(NRD_MODULE, NRD_MAX_ACCUMULATED_FRAME_NUM, "78");
        changed |= setAttr(NRD_MODULE, NRD_MAX_FAST_ACCUMULATED_FRAME_NUM, "5");
        changed |= setAttr(NRD_MODULE, NRD_MAX_STABILIZED_FRAME_NUM, "82");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_FRAME_NUM, "3");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_BASE_PIXEL_STRIDE, "12");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_ALTERNATE_PIXEL_STRIDE, "12");
        changed |= setAttr(NRD_MODULE, NRD_FAST_HISTORY_CLAMPING_SIGMA_SCALE, "1.6");
        changed |= setAttr(NRD_MODULE, NRD_DIFFUSE_PREPASS_BLUR_RADIUS, "18.0");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PREPASS_BLUR_RADIUS, "30.0");
        changed |= setAttr(NRD_MODULE, NRD_MIN_HIT_DISTANCE_WEIGHT, "0.09");
        changed |= setAttr(NRD_MODULE, NRD_MIN_BLUR_RADIUS, "0.9");
        changed |= setAttr(NRD_MODULE, NRD_MAX_BLUR_RADIUS, "72.0");
        changed |= setAttr(NRD_MODULE, NRD_LOBE_ANGLE_FRACTION, "0.16");
        changed |= setAttr(NRD_MODULE, NRD_ROUGHNESS_FRACTION, "0.15");
        changed |= setAttr(NRD_MODULE, NRD_PLANE_DISTANCE_SENSITIVITY, "0.020");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PROBABILITY_THRESHOLD_MIN, "0.50");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PROBABILITY_THRESHOLD_MAX, "0.88");
        changed |= setAttr(NRD_MODULE, NRD_FIREFLY_SUPPRESSOR_MIN_RELATIVE_SCALE, "2.0");
        changed |= setAttr(NRD_MODULE, NRD_MIN_MATERIAL_FOR_DIFFUSE, "3.5");
        changed |= setAttr(NRD_MODULE, NRD_MIN_MATERIAL_FOR_SPECULAR, "3.5");
        changed |= setAttr(NRD_MODULE, NRD_HIT_DISTANCE_RECONSTRUCTION_MODE, "5x5");
        changed |= setAttr(NRD_MODULE, NRD_ENABLE_ANTI_FIREFLY, "render_pipeline.true");
        changed |= setAttr(FSR3_MODULE, FSR3_QUALITY_MODE,
            "render_pipeline.module.fsr_upscaler.attribute.quality_mode.native");
        changed |= setAttr(FSR3_MODULE, FSR3_SHARPNESS, "0.80");
        changed |= setAttr(XESS_MODULE, XESS_QUALITY_MODE,
            "render_pipeline.module.xess_sr.attribute.quality_mode.ultra_quality");
        changed |= setAttr(XESS_MODULE, XESS_PRE_EXPOSURE, "1.03");
        changed |= setAttr(DLSS_MODULE, DLSS_MODE_ATTRIBUTE,
            "render_pipeline.module.dlss.attribute.mode.quality");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_METHOD,
            "render_pipeline.module.tone_mapping.attribute.method.pbr_neutral");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MIDDLE_GREY, "0.18");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_UP_SPEED, "7.5");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_DOWN_SPEED, "6.8");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOG2_LUMINANCE_MIN, "-12.5");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOG2_LUMINANCE_MAX, "4.2");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOW_PERCENT, "0.004");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_HIGH_PERCENT, "0.992");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MIN_EXPOSURE, "0.015");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MAX_EXPOSURE, "2.2");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_ENABLE_AUTO_EXPOSURE, "render_pipeline.true");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_METERING_MODE,
            "render_pipeline.module.tone_mapping.attribute.exposure_metering_mode.center");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_CENTER_METERING_PERCENT, "20.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MANUAL_EXPOSURE, "1.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_BIAS, "0.04");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_SATURATION, "1.02");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_WHITE_POINT, "32.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_CLAMP_OUTPUT, "render_pipeline.true");
        return changed;
    }

    private static boolean applyUltraQualityProfile() {
        setRayBounces(8, false);
        setChunkBuildingBatchSize(12, false);
        setChunkBuildingTotalBatches(14, false);
        dlssMode = 2;
        upscalerQuality = 0;
        denoiserMode = 2;

        boolean changed = false;
        changed |= setAttr(RAY_TRACING_MODULE, RT_TERRAIN_MESHING_MODE,
            "render_pipeline.module.ray_tracing.attribute.terrain_meshing_mode.coplanar_merge");
        changed |= setAttr(RAY_TRACING_MODULE, RT_GREEDY_MERGE_MAX_SPAN, "38");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_GEOMETRY_MODE,
            "render_pipeline.module.ray_tracing.attribute.far_field_geometry_mode.exact_chunks");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_START_DISTANCE_CHUNKS, "72");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_MATERIAL_MODE,
            "render_pipeline.module.ray_tracing.attribute.far_field_material_mode.full_pbr");
        changed |= setAttr(RAY_TRACING_MODULE, RT_GLASS_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FOLIAGE_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DECORATION_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BLAS_INCLUSION_MODE,
            "render_pipeline.module.ray_tracing.attribute.blas_inclusion_mode.all_geometry");
        changed |= setAttr(RAY_TRACING_MODULE, RT_REFLECTION_RAY_MATERIAL_MODE,
            "render_pipeline.module.ray_tracing.attribute.reflection_ray_material_mode.all_materials");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DIFFUSE_GI_MODE,
            "render_pipeline.module.ray_tracing.attribute.diffuse_gi_mode.full_ray_tracing");
        changed |= setAttr(RAY_TRACING_MODULE, RT_CLOUD_VOLUME_MODE,
            "render_pipeline.module.ray_tracing.attribute.cloud_volume_mode.realistic_volume");
        changed |= setAttr(RAY_TRACING_MODULE, RT_TERRAIN_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_ENTITY_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BLOCK_ENTITY_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_TRANSPARENT_SPLIT_MODE,
            "render_pipeline.module.ray_tracing.attribute.transparent_split_mode.stochastic");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_CRIT_GLOW, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_DEATH_SMOKE_GLOW, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_CRIT_GLOW_STRENGTH, "0.82");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_DEATH_SMOKE_GLOW_STRENGTH, "0.54");
        changed |= setAttr(RAY_TRACING_MODULE, RT_NUM_RAY_BOUNCES, "8");
        changed |= setAttr(RAY_TRACING_MODULE, RT_USE_JITTER, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PBR_SAMPLING_MODE,
            "render_pipeline.module.ray_tracing.attribute.pbr_sampling.bilinear");
        changed |= setAttr(RAY_TRACING_MODULE, RT_USE_SHARC, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_SEPARATE_ENTITY_TERRAIN_ACCEL_STRUCTURES,
            "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BASIC_RADIANCE, "4.8");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DIRECT_LIGHT_STRENGTH, "1.0");
        changed |= setAttr(RAY_TRACING_MODULE, RT_INDIRECT_LIGHT_STRENGTH, "19.5");
        changed |= setAttr(NRD_MODULE, NRD_ANTILAG_LUMINANCE_SIGMA_SCALE, "3.3");
        changed |= setAttr(NRD_MODULE, NRD_ANTILAG_LUMINANCE_SENSITIVITY, "2.6");
        changed |= setAttr(NRD_MODULE, NRD_RESPONSIVE_ACCUMULATION_ROUGHNESS_THRESHOLD, "0.03");
        changed |= setAttr(NRD_MODULE, NRD_RESPONSIVE_ACCUMULATION_MIN_ACCUMULATED_FRAME_NUM, "3");
        changed |= setAttr(NRD_MODULE, NRD_MAX_ACCUMULATED_FRAME_NUM, "90");
        changed |= setAttr(NRD_MODULE, NRD_MAX_FAST_ACCUMULATED_FRAME_NUM, "6");
        changed |= setAttr(NRD_MODULE, NRD_MAX_STABILIZED_FRAME_NUM, "94");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_FRAME_NUM, "3");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_BASE_PIXEL_STRIDE, "10");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_ALTERNATE_PIXEL_STRIDE, "10");
        changed |= setAttr(NRD_MODULE, NRD_FAST_HISTORY_CLAMPING_SIGMA_SCALE, "1.5");
        changed |= setAttr(NRD_MODULE, NRD_DIFFUSE_PREPASS_BLUR_RADIUS, "14.0");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PREPASS_BLUR_RADIUS, "24.0");
        changed |= setAttr(NRD_MODULE, NRD_MIN_HIT_DISTANCE_WEIGHT, "0.08");
        changed |= setAttr(NRD_MODULE, NRD_MIN_BLUR_RADIUS, "0.8");
        changed |= setAttr(NRD_MODULE, NRD_MAX_BLUR_RADIUS, "60.0");
        changed |= setAttr(NRD_MODULE, NRD_LOBE_ANGLE_FRACTION, "0.15");
        changed |= setAttr(NRD_MODULE, NRD_ROUGHNESS_FRACTION, "0.14");
        changed |= setAttr(NRD_MODULE, NRD_PLANE_DISTANCE_SENSITIVITY, "0.018");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PROBABILITY_THRESHOLD_MIN, "0.52");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PROBABILITY_THRESHOLD_MAX, "0.90");
        changed |= setAttr(NRD_MODULE, NRD_FIREFLY_SUPPRESSOR_MIN_RELATIVE_SCALE, "1.9");
        changed |= setAttr(NRD_MODULE, NRD_MIN_MATERIAL_FOR_DIFFUSE, "3.0");
        changed |= setAttr(NRD_MODULE, NRD_MIN_MATERIAL_FOR_SPECULAR, "3.0");
        changed |= setAttr(NRD_MODULE, NRD_HIT_DISTANCE_RECONSTRUCTION_MODE, "5x5");
        changed |= setAttr(NRD_MODULE, NRD_ENABLE_ANTI_FIREFLY, "render_pipeline.true");
        changed |= setAttr(FSR3_MODULE, FSR3_QUALITY_MODE,
            "render_pipeline.module.fsr_upscaler.attribute.quality_mode.native");
        changed |= setAttr(FSR3_MODULE, FSR3_SHARPNESS, "0.84");
        changed |= setAttr(XESS_MODULE, XESS_QUALITY_MODE,
            "render_pipeline.module.xess_sr.attribute.quality_mode.ultra_quality_plus");
        changed |= setAttr(XESS_MODULE, XESS_PRE_EXPOSURE, "1.04");
        changed |= setAttr(DLSS_MODULE, DLSS_MODE_ATTRIBUTE,
            "render_pipeline.module.dlss.attribute.mode.quality");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_METHOD,
            "render_pipeline.module.tone_mapping.attribute.method.pbr_neutral");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MIDDLE_GREY, "0.18");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_UP_SPEED, "6.8");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_DOWN_SPEED, "6.2");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOG2_LUMINANCE_MIN, "-13.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOG2_LUMINANCE_MAX, "4.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOW_PERCENT, "0.003");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_HIGH_PERCENT, "0.994");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MIN_EXPOSURE, "0.012");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MAX_EXPOSURE, "2.4");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_ENABLE_AUTO_EXPOSURE, "render_pipeline.true");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_METERING_MODE,
            "render_pipeline.module.tone_mapping.attribute.exposure_metering_mode.center");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_CENTER_METERING_PERCENT, "18.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MANUAL_EXPOSURE, "1.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_BIAS, "0.08");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_SATURATION, "1.03");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_WHITE_POINT, "34.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_CLAMP_OUTPUT, "render_pipeline.true");
        return changed;
    }

    private static boolean applyExtremeQualityProfile() {
        setRayBounces(16, false);
        setChunkBuildingBatchSize(10, false);
        setChunkBuildingTotalBatches(12, false);
        dlssMode = 3;
        upscalerQuality = 0;
        denoiserMode = 2;

        boolean changed = false;
        changed |= setAttr(RAY_TRACING_MODULE, RT_TERRAIN_MESHING_MODE,
            "render_pipeline.module.ray_tracing.attribute.terrain_meshing_mode.coplanar_merge");
        changed |= setAttr(RAY_TRACING_MODULE, RT_GREEDY_MERGE_MAX_SPAN, "42");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_GEOMETRY_MODE,
            "render_pipeline.module.ray_tracing.attribute.far_field_geometry_mode.exact_chunks");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_START_DISTANCE_CHUNKS, "96");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FAR_FIELD_MATERIAL_MODE,
            "render_pipeline.module.ray_tracing.attribute.far_field_material_mode.full_pbr");
        changed |= setAttr(RAY_TRACING_MODULE, RT_GLASS_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_FOLIAGE_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DECORATION_PATH_MODE,
            "render_pipeline.module.ray_tracing.attribute.geometry_path_mode.blas");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BLAS_INCLUSION_MODE,
            "render_pipeline.module.ray_tracing.attribute.blas_inclusion_mode.all_geometry");
        changed |= setAttr(RAY_TRACING_MODULE, RT_REFLECTION_RAY_MATERIAL_MODE,
            "render_pipeline.module.ray_tracing.attribute.reflection_ray_material_mode.all_materials");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DIFFUSE_GI_MODE,
            "render_pipeline.module.ray_tracing.attribute.diffuse_gi_mode.full_ray_tracing");
        changed |= setAttr(RAY_TRACING_MODULE, RT_CLOUD_VOLUME_MODE,
            "render_pipeline.module.ray_tracing.attribute.cloud_volume_mode.realistic_volume");
        changed |= setAttr(RAY_TRACING_MODULE, RT_TERRAIN_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_ENTITY_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BLOCK_ENTITY_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_UPDATE_INTERVAL_FRAMES, "1");
        changed |= setAttr(RAY_TRACING_MODULE, RT_TRANSPARENT_SPLIT_MODE,
            "render_pipeline.module.ray_tracing.attribute.transparent_split_mode.stochastic");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_CRIT_GLOW, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_DEATH_SMOKE_GLOW, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_CRIT_GLOW_STRENGTH, "1.0");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PARTICLE_DEATH_SMOKE_GLOW_STRENGTH, "0.72");
        changed |= setAttr(RAY_TRACING_MODULE, RT_NUM_RAY_BOUNCES, "16");
        changed |= setAttr(RAY_TRACING_MODULE, RT_USE_JITTER, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_PBR_SAMPLING_MODE,
            "render_pipeline.module.ray_tracing.attribute.pbr_sampling.bilinear");
        changed |= setAttr(RAY_TRACING_MODULE, RT_USE_SHARC, "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_SEPARATE_ENTITY_TERRAIN_ACCEL_STRUCTURES,
            "render_pipeline.true");
        changed |= setAttr(RAY_TRACING_MODULE, RT_BASIC_RADIANCE, "4.6");
        changed |= setAttr(RAY_TRACING_MODULE, RT_DIRECT_LIGHT_STRENGTH, "1.0");
        changed |= setAttr(RAY_TRACING_MODULE, RT_INDIRECT_LIGHT_STRENGTH, "21.0");
        changed |= setAttr(NRD_MODULE, NRD_ANTILAG_LUMINANCE_SIGMA_SCALE, "3.0");
        changed |= setAttr(NRD_MODULE, NRD_ANTILAG_LUMINANCE_SENSITIVITY, "2.3");
        changed |= setAttr(NRD_MODULE, NRD_RESPONSIVE_ACCUMULATION_ROUGHNESS_THRESHOLD, "0.02");
        changed |= setAttr(NRD_MODULE, NRD_RESPONSIVE_ACCUMULATION_MIN_ACCUMULATED_FRAME_NUM, "3");
        changed |= setAttr(NRD_MODULE, NRD_MAX_ACCUMULATED_FRAME_NUM, "112");
        changed |= setAttr(NRD_MODULE, NRD_MAX_FAST_ACCUMULATED_FRAME_NUM, "8");
        changed |= setAttr(NRD_MODULE, NRD_MAX_STABILIZED_FRAME_NUM, "116");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_FRAME_NUM, "3");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_BASE_PIXEL_STRIDE, "8");
        changed |= setAttr(NRD_MODULE, NRD_HISTORY_FIX_ALTERNATE_PIXEL_STRIDE, "8");
        changed |= setAttr(NRD_MODULE, NRD_FAST_HISTORY_CLAMPING_SIGMA_SCALE, "1.4");
        changed |= setAttr(NRD_MODULE, NRD_DIFFUSE_PREPASS_BLUR_RADIUS, "10.0");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PREPASS_BLUR_RADIUS, "18.0");
        changed |= setAttr(NRD_MODULE, NRD_MIN_HIT_DISTANCE_WEIGHT, "0.07");
        changed |= setAttr(NRD_MODULE, NRD_MIN_BLUR_RADIUS, "0.7");
        changed |= setAttr(NRD_MODULE, NRD_MAX_BLUR_RADIUS, "44.0");
        changed |= setAttr(NRD_MODULE, NRD_LOBE_ANGLE_FRACTION, "0.14");
        changed |= setAttr(NRD_MODULE, NRD_ROUGHNESS_FRACTION, "0.13");
        changed |= setAttr(NRD_MODULE, NRD_PLANE_DISTANCE_SENSITIVITY, "0.016");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PROBABILITY_THRESHOLD_MIN, "0.55");
        changed |= setAttr(NRD_MODULE, NRD_SPECULAR_PROBABILITY_THRESHOLD_MAX, "0.92");
        changed |= setAttr(NRD_MODULE, NRD_FIREFLY_SUPPRESSOR_MIN_RELATIVE_SCALE, "1.8");
        changed |= setAttr(NRD_MODULE, NRD_MIN_MATERIAL_FOR_DIFFUSE, "2.5");
        changed |= setAttr(NRD_MODULE, NRD_MIN_MATERIAL_FOR_SPECULAR, "2.5");
        changed |= setAttr(NRD_MODULE, NRD_HIT_DISTANCE_RECONSTRUCTION_MODE, "5x5");
        changed |= setAttr(NRD_MODULE, NRD_ENABLE_ANTI_FIREFLY, "render_pipeline.true");
        changed |= setAttr(FSR3_MODULE, FSR3_QUALITY_MODE,
            "render_pipeline.module.fsr_upscaler.attribute.quality_mode.native");
        changed |= setAttr(FSR3_MODULE, FSR3_SHARPNESS, "0.88");
        changed |= setAttr(XESS_MODULE, XESS_QUALITY_MODE,
            "render_pipeline.module.xess_sr.attribute.quality_mode.native");
        changed |= setAttr(XESS_MODULE, XESS_PRE_EXPOSURE, "1.05");
        changed |= setAttr(DLSS_MODULE, DLSS_MODE_ATTRIBUTE,
            "render_pipeline.module.dlss.attribute.mode.dlaa");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_METHOD,
            "render_pipeline.module.tone_mapping.attribute.method.pbr_neutral");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MIDDLE_GREY, "0.18");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_UP_SPEED, "6.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_DOWN_SPEED, "5.6");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOG2_LUMINANCE_MIN, "-13.5");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOG2_LUMINANCE_MAX, "4.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_LOW_PERCENT, "0.002");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_HIGH_PERCENT, "0.996");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MIN_EXPOSURE, "0.010");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MAX_EXPOSURE, "2.6");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_ENABLE_AUTO_EXPOSURE, "render_pipeline.true");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_METERING_MODE,
            "render_pipeline.module.tone_mapping.attribute.exposure_metering_mode.center");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_CENTER_METERING_PERCENT, "16.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_MANUAL_EXPOSURE, "1.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_EXPOSURE_BIAS, "0.12");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_SATURATION, "1.04");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_WHITE_POINT, "36.0");
        changed |= setAttr(TONE_MAPPING_MODULE, TM_CLAMP_OUTPUT, "render_pipeline.true");
        return changed;
    }

    private static boolean setAttr(String moduleName, String attributeName, String value) {
        return Pipeline.setModuleAttributeValue(moduleName, attributeName, value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
