package com.radiance.client.option;

import com.radiance.client.pipeline.Pipeline;
import java.util.Objects;

public final class EnvironmentRenderStyles {

    public static final String RAY_TRACING_MODULE = "render_pipeline.module.ray_tracing.name";
    public static final String WATER_SURFACE_MODE_ATTRIBUTE =
        "render_pipeline.module.ray_tracing.attribute.water_surface_mode";
    public static final String CLOUD_VOLUME_MODE_ATTRIBUTE =
        "render_pipeline.module.ray_tracing.attribute.cloud_volume_mode";
    public static final float WATER_SURFACE_SENTINEL = 31.25f;

    private EnvironmentRenderStyles() {
    }

    public static WaterSurfaceMode waterSurfaceMode() {
        return WaterSurfaceMode.fromKey(Pipeline.getModuleAttributeValue(RAY_TRACING_MODULE,
            WATER_SURFACE_MODE_ATTRIBUTE, WaterSurfaceMode.SPARKLING.key()));
    }

    public static CloudVolumeMode cloudVolumeMode() {
        return CloudVolumeMode.fromKey(Pipeline.getModuleAttributeValue(RAY_TRACING_MODULE,
            CLOUD_VOLUME_MODE_ATTRIBUTE, CloudVolumeMode.EFFICIENT_VOLUME.key()));
    }

    public enum WaterSurfaceMode {
        NATIVE_LIKE("render_pipeline.module.ray_tracing.attribute.water_surface_mode.native_like",
            0.0f),
        SPARKLING("render_pipeline.module.ray_tracing.attribute.water_surface_mode.sparkling",
            1.0f);

        private final String key;
        private final float shaderId;

        WaterSurfaceMode(String key, float shaderId) {
            this.key = key;
            this.shaderId = shaderId;
        }

        public String key() {
            return key;
        }

        public float shaderId() {
            return shaderId;
        }

        public static WaterSurfaceMode fromKey(String value) {
            for (WaterSurfaceMode mode : values()) {
                if (Objects.equals(mode.key, value)) {
                    return mode;
                }
            }
            return SPARKLING;
        }
    }

    public enum CloudVolumeMode {
        NATIVE("render_pipeline.module.ray_tracing.attribute.cloud_volume_mode.native", 0.0f),
        EFFICIENT_VOLUME(
            "render_pipeline.module.ray_tracing.attribute.cloud_volume_mode.efficient_volume",
            1.0f),
        REALISTIC_VOLUME(
            "render_pipeline.module.ray_tracing.attribute.cloud_volume_mode.realistic_volume",
            2.0f);

        private final String key;
        private final float shaderId;

        CloudVolumeMode(String key, float shaderId) {
            this.key = key;
            this.shaderId = shaderId;
        }

        public String key() {
            return key;
        }

        public float shaderId() {
            return shaderId;
        }

        public static CloudVolumeMode fromKey(String value) {
            for (CloudVolumeMode mode : values()) {
                if (Objects.equals(mode.key, value)) {
                    return mode;
                }
            }
            return EFFICIENT_VOLUME;
        }
    }
}
