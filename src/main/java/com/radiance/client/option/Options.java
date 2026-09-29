package com.radiance.client.option;

import com.radiance.client.RadianceClient;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.proxy.world.ChunkProxy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.minecraft.client.MinecraftClient;

public class Options {

    public static final String OPTION_PROPERTIES = "options.properties";

    public static final String CATEGORY_GAMEPLAY = "options.video.category.gameplay";
    public static final String CATEGORY_WINDOW = "options.video.category.window";
    public static final String CATEGORY_DLSS = "options.video.category.dlss";
    public static final String CATEGORY_FRAME_GENERATION = "options.video.category.frame_generation";
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
    public static final String UPSCALER_TYPE_KEY = "options.video.upscaler_type";
    public static final String UPSCALER_QUALITY_KEY = "options.video.upscaler_quality";
    public static final String FRAME_GENERATION_MODE_KEY = "options.video.frame_generation_mode";
    public static final String FRAME_GENERATION_METHOD_KEY = "options.video.frame_generation_method";
    public static final String FRAME_GENERATION_MULTIPLIER_KEY = "options.video.frame_generation_multiplier";
    public static final String DENOISER_MODE_KEY = "options.video.denoiser_mode";
    public static final String RAY_BOUNCES_KEY = "options.video.ray_bounces";
    public static final String CHUNK_BUILDING_BATCH_SIZE_KEY = "options.video.chunk_building_batch_size";
    public static final String CHUNK_BUILDING_TOTAL_BATCHES_KEY = "options.video.chunk_building_total_batches";
    public static final String CHUNK_BUILDING_THREADS_KEY = "options.video.chunk_building_threads";
    public static final String COLLECT_CHUNK_EMISSION_KEY = "options.video.collect_chunk_emission";
    public static final String SHADER_PACK_SETUP_KEY = "options.video.shader_pack_setup";
    public static final String PIPELINE_SETUP_KEY = "options.video.pipeline_setup";

    public static final String UPSCALER_TYPE_NATIVE = "options.video.upscaler_type.native";
    public static final String UPSCALER_TYPE_FSR3 = "options.video.upscaler_type.fsr3";
    public static final String FRAME_GENERATION_MODE_OFF = "options.video.frame_generation_mode.off";
    public static final String FRAME_GENERATION_MODE_DLSS = "options.video.frame_generation_mode.dlss";
    public static final String FRAME_GENERATION_MODE_FSR = "options.video.frame_generation_mode.fsr";
    public static final int FRAME_GENERATION_OFF = 0;
    public static final int FRAME_GENERATION_DLSS = 1;
    public static final int FRAME_GENERATION_FSR = 2;

    public static final String UPSCALER_QUALITY_NATIVEAA = "options.video.upscaler_quality.nativeaa";
    public static final String UPSCALER_QUALITY_QUALITY = "options.video.upscaler_quality.quality";
    public static final String UPSCALER_QUALITY_BALANCED = "options.video.upscaler_quality.balanced";
    public static final String UPSCALER_QUALITY_PERFORMANCE = "options.video.upscaler_quality.performance";
    public static final String DENOISER_MODE_DLSS = "options.video.denoiser_mode.dlss";
    public static final String DENOISER_MODE_SVGF = "options.video.denoiser_mode.svgf";
    public static final String DENOISER_MODE_NRD = "options.video.denoiser_mode.nrd";
    public static final String DENOISER_MODE_TEMPORAL = "options.video.denoiser_mode.temporal";
    public static final int CHUNK_BUILDING_BATCH_SIZE_MIN = 1;
    public static final int CHUNK_BUILDING_BATCH_SIZE_MAX = 32;
    public static final int CHUNK_BUILDING_TOTAL_BATCHES_MIN = 1;
    public static final int CHUNK_BUILDING_TOTAL_BATCHES_MAX = 32;
    public static final int CHUNK_BUILDING_THREADS_MIN = 1;
    private static final int DEFAULT_CHUNK_BUILDING_BATCH_SIZE = 8;
    private static final int DEFAULT_CHUNK_BUILDING_TOTAL_BATCHES = 4;
    private static final int DEFAULT_CHUNK_BUILDING_THREADS = 4;
    public static int maxFps = 260;
    public static int inactivityFpsLimit = 260;
    public static boolean vsync = true;
    public static int dlssMode = 1;
    public static int upscalerType = 1;
    public static int upscalerQuality = 1;
    public static int frameGenerationMode = 0;
    public static int frameGenerationMultiplier = 2;
    private static boolean frameGenerationDefaultPending = true;
    public static int denoiserMode = 1;
    public static int rayBounces = 4;
    public static int chunkBuildingBatchSize = DEFAULT_CHUNK_BUILDING_BATCH_SIZE;
    public static int chunkBuildingTotalBatches = DEFAULT_CHUNK_BUILDING_TOTAL_BATCHES;
    public static int chunkBuildingThreads = clampChunkBuildingThreads(DEFAULT_CHUNK_BUILDING_THREADS);
    public static boolean collectChunkEmission = true;

    public static int getMaxChunkBuildingThreads() {
        int processorCount = Runtime.getRuntime().availableProcessors();
        boolean has64BitArchitecture = System.getProperty("os.arch", "").contains("64");
        return Math.max(1, has64BitArchitecture ? processorCount : Math.min(processorCount, 4));
    }

    public static int clampChunkBuildingThreads(int requestedChunkBuildingThreads) {
        return Math.max(CHUNK_BUILDING_THREADS_MIN,
            Math.min(requestedChunkBuildingThreads, getMaxChunkBuildingThreads()));
    }

    public static int clampChunkBuildingBatchSize(int requestedChunkBuildingBatchSize) {
        return Math.max(CHUNK_BUILDING_BATCH_SIZE_MIN,
            Math.min(requestedChunkBuildingBatchSize, CHUNK_BUILDING_BATCH_SIZE_MAX));
    }

    public static int clampChunkBuildingTotalBatches(int requestedChunkBuildingTotalBatches) {
        return Math.max(CHUNK_BUILDING_TOTAL_BATCHES_MIN,
            Math.min(requestedChunkBuildingTotalBatches, CHUNK_BUILDING_TOTAL_BATCHES_MAX));
    }

    public static void readOptions() {
        Path optionsPath = RadianceClient.radianceDir.resolve(OPTION_PROPERTIES);
        if (!Files.exists(optionsPath)) {
            overwriteConfig();
            return;
        }

        Properties optionsProperties = new Properties();
        try (InputStream inputStream = Files.newInputStream(optionsPath)) {
            optionsProperties.load(inputStream);
            frameGenerationDefaultPending = !optionsProperties.containsKey("frameGenerationMode");

            setMaxFps(Integer.parseInt(optionsProperties.getProperty("maxFps", String.valueOf(maxFps))), false);
            setInactivityFpsLimit(Integer.parseInt(
                    optionsProperties.getProperty("inactivityFpsLimit", String.valueOf(inactivityFpsLimit))),
                false);
            setVsync(Boolean.parseBoolean(optionsProperties.getProperty("vsync", String.valueOf(vsync))),
                false);
            setFrameGenerationMode(Integer.parseInt(optionsProperties.getProperty("frameGenerationMode",
                    String.valueOf(frameGenerationMode))),
                false);
            setFrameGenerationMultiplier(Integer.parseInt(optionsProperties.getProperty("frameGenerationMultiplier",
                    String.valueOf(frameGenerationMultiplier))),
                false);
            setChunkBuildingBatchSize(Integer.parseInt(optionsProperties.getProperty("chunkBuildingBatchSize",
                    String.valueOf(chunkBuildingBatchSize))),
                false);
            setChunkBuildingTotalBatches(
                Integer.parseInt(optionsProperties.getProperty("chunkBuildingTotalBatches",
                    String.valueOf(chunkBuildingTotalBatches))), false);
            setChunkBuildingThreads(
                Integer.parseInt(optionsProperties.getProperty("chunkBuildingThreads",
                    String.valueOf(chunkBuildingThreads))), false);
            setCollectChunkEmission(Boolean.parseBoolean(optionsProperties.getProperty("collectChunkEmission",
                    String.valueOf(collectChunkEmission))),
                false);

            overwriteConfig();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void overwriteConfig() {
        Path optionsPath = RadianceClient.radianceDir.resolve(OPTION_PROPERTIES);
        Properties optionsProperties = new Properties();
        optionsProperties.setProperty("maxFps", String.valueOf(maxFps));
        optionsProperties.setProperty("inactivityFpsLimit", String.valueOf(inactivityFpsLimit));
        optionsProperties.setProperty("vsync", String.valueOf(vsync));
        optionsProperties.setProperty("dlssMode", String.valueOf(dlssMode));
        optionsProperties.setProperty("upscalerType", String.valueOf(upscalerType));
        optionsProperties.setProperty("upscalerQuality", String.valueOf(upscalerQuality));
        if (!frameGenerationDefaultPending) {
            optionsProperties.setProperty("frameGenerationMode", String.valueOf(frameGenerationMode));
        }
        optionsProperties.setProperty("frameGenerationMultiplier", String.valueOf(frameGenerationMultiplier));
        optionsProperties.setProperty("denoiserMode", String.valueOf(denoiserMode));
        optionsProperties.setProperty("rayBounces", String.valueOf(rayBounces));
        optionsProperties.setProperty("chunkBuildingBatchSize", String.valueOf(chunkBuildingBatchSize));
        optionsProperties.setProperty("chunkBuildingTotalBatches", String.valueOf(chunkBuildingTotalBatches));
        optionsProperties.setProperty("chunkBuildingThreads", String.valueOf(chunkBuildingThreads));
        optionsProperties.setProperty("collectChunkEmission", String.valueOf(collectChunkEmission));

        try {
            Files.createDirectories(optionsPath.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (OutputStream outputStream = Files.newOutputStream(optionsPath)) {
            optionsProperties.store(outputStream, "Options");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public native static void nativeSetMaxFps(int maxFps, boolean shouldWriteConfig);

    public static void setMaxFps(int maxFps, boolean shouldWriteConfig) {
        Options.maxFps = maxFps;
        nativeSetMaxFps(maxFps, shouldWriteConfig);
        if (shouldWriteConfig) {
            overwriteConfig();
        }
    }

    public native static void nativeSetInactivityFpsLimit(int inactivityFpsLimit, boolean shouldWriteConfig);

    public static void setInactivityFpsLimit(int inactivityFpsLimit, boolean shouldWriteConfig) {
        Options.inactivityFpsLimit = inactivityFpsLimit;
        nativeSetInactivityFpsLimit(inactivityFpsLimit, shouldWriteConfig);
        if (shouldWriteConfig) {
            overwriteConfig();
        }
    }

    public native static void nativeSetVsync(boolean vsync, boolean shouldWriteConfig);

    public static void setVsync(boolean vsync, boolean shouldWriteConfig) {
        Options.vsync = vsync;
        nativeSetVsync(vsync, shouldWriteConfig);
        if (shouldWriteConfig) {
            overwriteConfig();
        }
    }

    public native static void nativeSetFrameGenerationMode(int frameGenerationMode, boolean shouldWriteConfig);

    public native static int nativeGetAvailableFrameGenerationModeMask();

    public native static int nativeGetMaxFrameGenerationMultiplier(int frameGenerationMode);

    public static void setFrameGenerationMode(int frameGenerationMode, boolean shouldWriteConfig) {
        Options.frameGenerationMode = Math.max(FRAME_GENERATION_OFF, Math.min(frameGenerationMode,
            FRAME_GENERATION_FSR));
        nativeSetFrameGenerationMode(Options.frameGenerationMode, shouldWriteConfig);
        if (shouldWriteConfig) {
            overwriteConfig();
        }
    }

    public native static void nativeSetFrameGenerationMultiplier(int frameGenerationMultiplier,
        boolean shouldWriteConfig);

    public static void setFrameGenerationMultiplier(int frameGenerationMultiplier, boolean shouldWriteConfig) {
        Options.frameGenerationMultiplier = Math.max(2, Math.min(frameGenerationMultiplier, 4));
        nativeSetFrameGenerationMultiplier(Options.frameGenerationMultiplier, shouldWriteConfig);
        if (shouldWriteConfig) {
            overwriteConfig();
        }
    }

    public static List<Integer> getAvailableFrameGenerationModes() {
        int modeMask = nativeGetAvailableFrameGenerationModeMask();
        ArrayList<Integer> modes = new ArrayList<>();
        modes.add(FRAME_GENERATION_OFF);
        if ((modeMask & (1 << FRAME_GENERATION_DLSS)) != 0) {
            modes.add(FRAME_GENERATION_DLSS);
        }
        if ((modeMask & (1 << FRAME_GENERATION_FSR)) != 0) {
            modes.add(FRAME_GENERATION_FSR);
        }
        return modes;
    }

    public static boolean isFrameGenerationModeAvailable(int mode) {
        return getAvailableFrameGenerationModes().contains(mode);
    }

    public static int getFirstAvailableFrameGenerationMode() {
        for (int mode : getAvailableFrameGenerationModes()) {
            if (mode != FRAME_GENERATION_OFF) {
                return mode;
            }
        }
        return FRAME_GENERATION_OFF;
    }

    public static void applyDefaultFrameGeneration() {
        if (!frameGenerationDefaultPending) {
            return;
        }
        int defaultMode = getFirstAvailableFrameGenerationMode();
        frameGenerationDefaultPending = false;
        setFrameGenerationMultiplier(2, false);
        setFrameGenerationMode(defaultMode, true);
    }

    public static int getFrameGenerationMultiplierReferenceMode() {
        if (frameGenerationMode != FRAME_GENERATION_OFF && isFrameGenerationModeAvailable(frameGenerationMode)) {
            return frameGenerationMode;
        }
        return getFirstAvailableFrameGenerationMode();
    }

    public static int getMaxFrameGenerationMultiplier(int mode) {
        if (mode == FRAME_GENERATION_OFF || !isFrameGenerationModeAvailable(mode)) {
            return 1;
        }
        return Math.max(1, Math.min(nativeGetMaxFrameGenerationMultiplier(mode), 4));
    }

    public static List<Integer> getAvailableFrameGenerationMultipliers(int mode) {
        int maxMultiplier = getMaxFrameGenerationMultiplier(mode);
        ArrayList<Integer> multipliers = new ArrayList<>();
        for (int multiplier = 2; multiplier <= maxMultiplier; multiplier++) {
            multipliers.add(multiplier);
        }
        return multipliers;
    }

    public static void sanitizeFrameGenerationOptionsForAvailableHardware(boolean shouldWriteConfig) {
        if (!isFrameGenerationModeAvailable(frameGenerationMode)) {
            setFrameGenerationMode(getFirstAvailableFrameGenerationMode(), shouldWriteConfig);
        }

        int multiplierReferenceMode = getFrameGenerationMultiplierReferenceMode();
        int maxMultiplier = getMaxFrameGenerationMultiplier(multiplierReferenceMode);
        if (maxMultiplier >= 2 && frameGenerationMultiplier > maxMultiplier) {
            setFrameGenerationMultiplier(maxMultiplier, shouldWriteConfig);
        }
    }

    public static String frameGenerationModeTranslationKey(int mode) {
        return switch (mode) {
            case FRAME_GENERATION_DLSS -> FRAME_GENERATION_MODE_DLSS;
            case FRAME_GENERATION_FSR -> FRAME_GENERATION_MODE_FSR;
            case FRAME_GENERATION_OFF -> FRAME_GENERATION_MODE_OFF;
            default -> FRAME_GENERATION_MODE_OFF;
        };
    }

    public native static void nativeSetChunkBuildingBatchSize(int chunkBuildingBatchSize,
        boolean shouldWriteConfig);

    public static void setChunkBuildingBatchSize(int chunkBuildingBatchSize, boolean shouldWriteConfig) {
        Options.chunkBuildingBatchSize = clampChunkBuildingBatchSize(chunkBuildingBatchSize);
        nativeSetChunkBuildingBatchSize(Options.chunkBuildingBatchSize, shouldWriteConfig);
        if (shouldWriteConfig) {
            overwriteConfig();
        }
    }

    public native static void nativeSetChunkBuildingTotalBatches(int chunkBuildingTotalBatches,
        boolean shouldWriteConfig);

    public static void setChunkBuildingTotalBatches(int chunkBuildingTotalBatches, boolean shouldWriteConfig) {
        Options.chunkBuildingTotalBatches = clampChunkBuildingTotalBatches(chunkBuildingTotalBatches);
        nativeSetChunkBuildingTotalBatches(Options.chunkBuildingTotalBatches, shouldWriteConfig);
        if (shouldWriteConfig) {
            overwriteConfig();
        }
    }

    public native static void nativeSetChunkBuildingThreads(int chunkBuildingThreads,
        boolean shouldWriteConfig);

    public static void setChunkBuildingThreads(int requestedChunkBuildingThreads, boolean shouldWriteConfig) {
        Options.chunkBuildingThreads = clampChunkBuildingThreads(requestedChunkBuildingThreads);
        nativeSetChunkBuildingThreads(Options.chunkBuildingThreads, shouldWriteConfig);
        if (shouldWriteConfig) {
            overwriteConfig();
        }
    }

    public native static void nativeSetCollectChunkEmission(boolean collectChunkEmission,
        boolean shouldWriteConfig);

    public static void setCollectChunkEmission(boolean collectChunkEmission, boolean shouldWriteConfig) {
        boolean hasCollectChunkEmissionChanged = Options.collectChunkEmission != collectChunkEmission;
        Options.collectChunkEmission = collectChunkEmission;
        nativeSetCollectChunkEmission(collectChunkEmission, shouldWriteConfig);

        if (hasCollectChunkEmissionChanged) {
            if (collectChunkEmission) {
                MinecraftClient client = MinecraftClient.getInstance();
                client.reloadResources().thenRun(() -> client.execute(() -> {
                    if (Options.collectChunkEmission) {
                        ChunkProxy.rebuildAll();
                    }
                }));
            } else if (shouldWriteConfig) {
                Pipeline.ensureSelectedShaderPackAvailable();
            }
        }

        if (shouldWriteConfig) {
            overwriteConfig();
        }
    }
}
