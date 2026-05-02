package com.radiance.client.option;

import com.radiance.client.RadianceClient;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.proxy.world.ChunkProxy;
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
    public static final String CATEGORY_HDR = "options.video.category.hdr";
    public static final String CATEGORY_DLSS = "options.video.category.dlss";
    public static final String CATEGORY_RAY_TRACING = "options.video.category.ray_tracing";
    public static final String CATEGORY_UPSCALER = "options.video.category.upscaler";
    public static final String CATEGORY_TERRAIN = "options.video.category.terrain";
    public static final String CATEGORY_PIPELINE = "options.video.category.pipeline";
    public static final String CATEGORY_SUN = "options.video.category.sun";

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
    public static final String DENOISER_MODE_KEY = "options.video.denoiser_mode";
    public static final String RAY_BOUNCES_KEY = "options.video.ray_bounces";
    public static final String CHUNK_BUILDING_BATCH_SIZE_KEY = "options.video.chunk_building_batch_size";
    public static final String CHUNK_BUILDING_TOTAL_BATCHES_KEY = "options.video.chunk_building_total_batches";
    public static final String CHUNK_BUILDING_THREADS_KEY = "options.video.chunk_building_threads";
    public static final String COLLECT_CHUNK_EMISSION_KEY = "options.video.collect_chunk_emission";
    public static final String SHADER_PACK_SETUP_KEY = "options.video.shader_pack_setup";
    public static final String PIPELINE_SETUP_KEY = "options.video.pipeline_setup";
    public static final String SUN_SIZE_KEY = "options.video.sun_size";
    public static final String SUN_PATH_TILT_KEY = "options.video.sun_path_tilt";
    public static final String HDR_ENABLED_KEY = "options.video.hdr_enabled";
    public static final String HDR_MIN_LUMINANCE_KEY = "options.video.hdr_min_luminance";
    public static final String HDR_MAX_LUMINANCE_KEY = "options.video.hdr_max_luminance";
    public static final String HDR_ROLL_OFF_KEY = "options.video.hdr_roll_off";
    public static final String SDR_BRIGHTNESS_KEY = "options.video.sdr_brightness";

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
    public static boolean hdrEnabled = true;
    public static float hdrMinLuminance = 0.5f;
    public static float hdrMaxLuminance = 1000.0f;
    public static float hdrRollOff = 1.0f;
    public static float sdrBrightness = 200.0f;
    public static int dlssMode = 1;
    public static int upscalerType = 1;
    public static int upscalerQuality = 1;
    public static int denoiserMode = 1;
    public static int rayBounces = 4;
    public static int chunkBuildingBatchSize = 12;
    public static int chunkBuildingTotalBatches = 12;
    public static int chunkBuildingThreads = getDefaultChunkBuildingThreads();
    public static boolean collectChunkEmission = false;
    public static int sunSize = 10;
    public static int sunPathTilt = 23;
    public static int cloudDensityGradient = 10;
    public static int cloudOpacity = 80;
    public static int cloudAnisotropy = 80;
    public static int cloudEdgeSoftness = 50;

    public static int getMaxChunkBuildingThreads() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        boolean is64Bits = System.getProperty("os.arch", "").contains("64");
        return Math.max(1, is64Bits ? availableProcessors : Math.min(availableProcessors, 4));
    }

    public static int clampChunkBuildingThreads(int chunkBuildingThreads) {
        return Math.max(1, Math.min(chunkBuildingThreads, getMaxChunkBuildingThreads()));
    }

    private static int getDefaultChunkBuildingThreads() {
        return clampChunkBuildingThreads(
            Math.max(1, (int) (Runtime.getRuntime().availableProcessors() * 0.6)));
    }

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
            setHdrEnabled(Boolean.parseBoolean(props.getProperty("hdrEnabled", String.valueOf(hdrEnabled))),
                false);
            setHdrMinLuminance(Float.parseFloat(props.getProperty("hdrMinLuminance",
                    String.valueOf(hdrMinLuminance))),
                false);
            setHdrMaxLuminance(Float.parseFloat(props.getProperty("hdrMaxLuminance",
                    String.valueOf(hdrMaxLuminance))),
                false);
            setHdrRollOff(Float.parseFloat(props.getProperty("hdrRollOff",
                    String.valueOf(hdrRollOff))),
                false);
            setSdrBrightness(Float.parseFloat(props.getProperty("sdrBrightness",
                    String.valueOf(sdrBrightness))),
                false);
            cloudDensityGradient = Integer.parseInt(
                props.getProperty("cloudDensityGradient", String.valueOf(cloudDensityGradient)));
            cloudOpacity = Integer.parseInt(
                props.getProperty("cloudOpacity", String.valueOf(cloudOpacity)));
            cloudAnisotropy = Integer.parseInt(
                props.getProperty("cloudAnisotropy", String.valueOf(cloudAnisotropy)));
            cloudEdgeSoftness = Integer.parseInt(
                props.getProperty("cloudEdgeSoftness", String.valueOf(cloudEdgeSoftness)));
            setChunkBuildingBatchSize(Integer.parseInt(props.getProperty("chunkBuildingBatchSize",
                    String.valueOf(chunkBuildingBatchSize))),
                false);
            setChunkBuildingTotalBatches(
                Integer.parseInt(props.getProperty("chunkBuildingTotalBatches",
                    String.valueOf(chunkBuildingTotalBatches))), false);
            setChunkBuildingThreads(
                Integer.parseInt(props.getProperty("chunkBuildingThreads",
                    String.valueOf(chunkBuildingThreads))), false);
            setCollectChunkEmission(Boolean.parseBoolean(props.getProperty("collectChunkEmission",
                    String.valueOf(collectChunkEmission))),
                false);
            setSunSize(Integer.parseInt(props.getProperty("sunSize",
                    String.valueOf(sunSize))),
                false);
            setSunPathTilt(Integer.parseInt(props.getProperty("sunPathTilt",
                    String.valueOf(sunPathTilt))),
                false);

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
        props.setProperty("hdrEnabled", String.valueOf(hdrEnabled));
        props.setProperty("hdrMinLuminance", String.valueOf(hdrMinLuminance));
        props.setProperty("hdrMaxLuminance", String.valueOf(hdrMaxLuminance));
        props.setProperty("hdrRollOff", String.valueOf(hdrRollOff));
        props.setProperty("sdrBrightness", String.valueOf(sdrBrightness));
        props.setProperty("dlssMode", String.valueOf(dlssMode));
        props.setProperty("upscalerType", String.valueOf(upscalerType));
        props.setProperty("upscalerQuality", String.valueOf(upscalerQuality));
        props.setProperty("denoiserMode", String.valueOf(denoiserMode));
        props.setProperty("rayBounces", String.valueOf(rayBounces));
        props.setProperty("chunkBuildingBatchSize", String.valueOf(chunkBuildingBatchSize));
        props.setProperty("chunkBuildingTotalBatches", String.valueOf(chunkBuildingTotalBatches));
        props.setProperty("chunkBuildingThreads", String.valueOf(chunkBuildingThreads));
        props.setProperty("collectChunkEmission", String.valueOf(collectChunkEmission));
        props.setProperty("sunSize", String.valueOf(sunSize));
        props.setProperty("sunPathTilt", String.valueOf(sunPathTilt));
        props.setProperty("cloudDensityGradient", String.valueOf(cloudDensityGradient));
        props.setProperty("cloudOpacity", String.valueOf(cloudOpacity));
        props.setProperty("cloudAnisotropy", String.valueOf(cloudAnisotropy));
        props.setProperty("cloudEdgeSoftness", String.valueOf(cloudEdgeSoftness));

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

    public native static void nativeSetHdrEnabled(boolean hdrEnabled, boolean write);

    public static void setHdrEnabled(boolean hdrEnabled, boolean write) {
        Options.hdrEnabled = hdrEnabled;
        nativeSetHdrEnabled(hdrEnabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetHdrMinLuminance(float hdrMinLuminance, boolean write);

    public static void setHdrMinLuminance(float hdrMinLuminance, boolean write) {
        Options.hdrMinLuminance = Math.max(hdrMinLuminance, 0.0f);
        nativeSetHdrMinLuminance(Options.hdrMinLuminance, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetHdrMaxLuminance(float hdrMaxLuminance, boolean write);

    public static void setHdrMaxLuminance(float hdrMaxLuminance, boolean write) {
        Options.hdrMaxLuminance = Math.max(hdrMaxLuminance, Options.hdrMinLuminance + 0.001f);
        nativeSetHdrMaxLuminance(Options.hdrMaxLuminance, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetHdrRollOff(float hdrRollOff, boolean write);

    public static void setHdrRollOff(float hdrRollOff, boolean write) {
        Options.hdrRollOff = Math.max(hdrRollOff, 0.001f);
        nativeSetHdrRollOff(Options.hdrRollOff, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static boolean nativeIsHdrActive();

    public static boolean isHdrActive() {
        return nativeIsHdrActive();
    }

    public native static void nativeSetSdrBrightness(float sdrBrightness, boolean write);

    public static void setSdrBrightness(float sdrBrightness, boolean write) {
        Options.sdrBrightness = Math.max(sdrBrightness, 1.0f);
        nativeSetSdrBrightness(Options.sdrBrightness, write);
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

    public static void setChunkBuildingThreads(int chunkBuildingThreads, boolean write) {
        Options.chunkBuildingThreads = clampChunkBuildingThreads(chunkBuildingThreads);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetCollectChunkEmission(boolean collectChunkEmission,
        boolean write);

    public static void setCollectChunkEmission(boolean collectChunkEmission, boolean write) {
        boolean changed = Options.collectChunkEmission != collectChunkEmission;
        Options.collectChunkEmission = collectChunkEmission;
        nativeSetCollectChunkEmission(collectChunkEmission, write);

        if (changed) {
            if (collectChunkEmission) {
                TextureProxy.flushEmissionTiles();
                ChunkProxy.rebuildAll();
            } else if (write) {
                Pipeline.ensureSelectedShaderPackAvailable();
            }
        }

        if (write) {
            overwriteConfig();
        }
    }

    public static void setSunSize(int sunSize, boolean write) {
        Options.sunSize = Math.max(1, Math.min(sunSize, 100));
        if (write) {
            overwriteConfig();
        }
    }

    public static void setSunPathTilt(int sunPathTilt, boolean write) {
        Options.sunPathTilt = Math.max(0, Math.min(sunPathTilt, 45));
        if (write) {
            overwriteConfig();
        }
    }
}
