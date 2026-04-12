package com.radiance.client.option;

import com.radiance.client.RadianceClient;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public static final String DLSS_MODE_ULTRA_PERFORMANCE_TOOLTIP = "options.video.dlss_mode.ultra_performance.tooltip";
    public static final String DLSS_MODE_PERFORMANCE_TOOLTIP = "options.video.dlss_mode.performance.tooltip";
    public static final String DLSS_MODE_BALANCED_TOOLTIP = "options.video.dlss_mode.balanced.tooltip";
    public static final String DLSS_MODE_QUALITY_TOOLTIP = "options.video.dlss_mode.quality.tooltip";
    public static final String DLSS_MODE_DLAA_TOOLTIP = "options.video.dlss_mode.dlaa.tooltip";

    public static final String DLSS_MODE_ULTRA_PERFORMANCE = "options.video.dlss_mode.ultra_performance";
    public static final String DLSS_MODE_PERFORMANCE = "options.video.dlss_mode.performance";
    public static final String DLSS_MODE_BALANCED = "options.video.dlss_mode.balanced";
    public static final String DLSS_MODE_QUALITY = "options.video.dlss_mode.quality";
    public static final String DLSS_MODE_DLAA = "options.video.dlss_mode.dlaa";

    public static final String DLSS_MODE_KEY = "options.video.dlss_mode";
    public static final String DLSS_FRAME_GEN_KEY = "options.video.dlss_frame_gen";
    public static final String DLSS_FRAME_GEN_OFF = "options.video.dlss_frame_gen.off";
    public static final String DLSS_FRAME_GEN_X2 = "options.video.dlss_frame_gen.x2";
    public static final String DLSS_FRAME_GEN_X3 = "options.video.dlss_frame_gen.x3";
    public static final String DLSS_FRAME_GEN_X4 = "options.video.dlss_frame_gen.x4";
    public static final String DLSS_FRAME_GEN_X5 = "options.video.dlss_frame_gen.x5";
    public static final String DLSS_FRAME_GEN_X6 = "options.video.dlss_frame_gen.x6";
    public static final String DLSS_FRAME_GEN_DYNAMIC = "options.video.dlss_frame_gen.dynamic";
    public static final String UPSCALER_TYPE_KEY = "options.video.upscaler_type";
    public static final String UPSCALER_QUALITY_KEY = "options.video.upscaler_quality";
    public static final String DENOISER_MODE_KEY = "options.video.denoiser_mode";
    public static final String RAY_BOUNCES_KEY = "options.video.ray_bounces";
    public static final String CHUNK_BUILDING_BATCH_SIZE_KEY = "options.video.chunk_building_batch_size";
    public static final String CHUNK_BUILDING_TOTAL_BATCHES_KEY = "options.video.chunk_building_total_batches";
    public static final String PIPELINE_SETUP_KEY = "options.video.pipeline_setup";

    public static final String UPSCALER_TYPE_NATIVE = "options.video.upscaler_type.native";
    public static final String UPSCALER_TYPE_FSR3 = "options.video.upscaler_type.fsr3";

    public static final String UPSCALER_QUALITY_NATIVEAA = "options.video.upscaler_quality.nativeaa";
    public static final String UPSCALER_QUALITY_QUALITY = "options.video.upscaler_quality.quality";
    public static final String UPSCALER_QUALITY_BALANCED = "options.video.upscaler_quality.balanced";
    public static final String UPSCALER_QUALITY_PERFORMANCE = "options.video.upscaler_quality.performance";
    public static final String DENOISER_MODE_DLSS = "options.video.denoiser_mode.dlss";
    public static final String DENOISER_MODE_SVGF = "options.video.denoiser_mode.svgf";
    public static final String DENOISER_MODE_NRD = "options.video.denoiser_mode.nrd";
    public static final String DENOISER_MODE_TEMPORAL = "options.video.denoiser_mode.temporal";
    public static int maxFps = 260;
    public static int inactivityFpsLimit = 260;
    public static boolean vsync = true;
    public static int dlssMode = 1;
    public static int upscalerType = 1;
    public static int upscalerQuality = 1;
    public static int denoiserMode = 1;
    public static int rayBounces = 4;
    public static int chunkBuildingBatchSize = 12;
    public static int chunkBuildingTotalBatches = 12;

    public static void readOptions() {
        Path path = RadianceClient.radianceDir.resolve(OPTION_PROPERTIES);
        if (!Files.exists(path)) {
//            System.out.println("Generating default options...");
            overwriteConfig();
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);

            setMaxFps(Integer.parseInt(props.getProperty("maxFps", String.valueOf(maxFps))), false);
            setInactivityFpsLimit(Integer.parseInt(
                    props.getProperty("inactivityFpsLimit", String.valueOf(inactivityFpsLimit))),
                false);
            setVsync(Boolean.parseBoolean(props.getProperty("vsync", String.valueOf(vsync))),
                false);
            setChunkBuildingBatchSize(Integer.parseInt(props.getProperty("chunkBuildingBatchSize",
                    String.valueOf(chunkBuildingBatchSize))),
                false);
            setChunkBuildingTotalBatches(
                Integer.parseInt(props.getProperty("chunkBuildingTotalBatches",
                    String.valueOf(chunkBuildingTotalBatches))), false);

            overwriteConfig();
//            System.out.println("Successfully read options: " + path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void overwriteConfig() {
        Path path = RadianceClient.radianceDir.resolve(OPTION_PROPERTIES);
        Properties props = new Properties();
        props.setProperty("maxFps", String.valueOf(maxFps));
        props.setProperty("inactivityFpsLimit", String.valueOf(inactivityFpsLimit));
        props.setProperty("vsync", String.valueOf(vsync));
        props.setProperty("dlssMode", String.valueOf(dlssMode));
        props.setProperty("upscalerType", String.valueOf(upscalerType));
        props.setProperty("upscalerQuality", String.valueOf(upscalerQuality));
        props.setProperty("denoiserMode", String.valueOf(denoiserMode));
        props.setProperty("rayBounces", String.valueOf(rayBounces));
        props.setProperty("chunkBuildingBatchSize", String.valueOf(chunkBuildingBatchSize));
        props.setProperty("chunkBuildingTotalBatches", String.valueOf(chunkBuildingTotalBatches));

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
}
