package com.radiance.client.pipeline;

public enum Presets {
    RT_DLSSRR("RT_DLSSRR", "render_pipeline.preset.rt_dlss"),
    RT_NRD("RT_NRD", "render_pipeline.preset.rt_nrd"),
    RT_NRD_FSR("RT_NRD_FSR", "render_pipeline.preset.rt_nrd_fsr"),
    RT_NRD_XESS("RT_NRD_XESS", "render_pipeline.preset.rt_nrd_xess"),
    DEFERRED_RT("DEFERRED_RT", "render_pipeline.preset.deferred_rt"),
    DEFERRED_RT_NRD("DEFERRED_RT_NRD", "render_pipeline.preset.deferred_rt_nrd"),
    DEFERRED_RT_NRD_FSR("DEFERRED_RT_NRD_FSR", "render_pipeline.preset.deferred_rt_nrd_fsr"),
    DEFERRED_RT_NRD_XESS("DEFERRED_RT_NRD_XESS", "render_pipeline.preset.deferred_rt_nrd_xess"),
    DEFERRED_RT_DLSSRR("DEFERRED_RT_DLSSRR", "render_pipeline.preset.deferred_rt_dlss"),
    ;

    public final String name;
    public final String key;

    Presets(String name, String key) {
        this.name = name;
        this.key = key;
    }
}
