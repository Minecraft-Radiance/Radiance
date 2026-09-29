package com.radiance.client.pipeline;

public enum Presets {
    RT_DLSSRR("RT_DLSSRR", "render_pipeline.preset.rt_dlss_rr"),
    RT_NRD_DLSS("RT_NRD_DLSS", "render_pipeline.preset.rt_nrd_dlss"),
    RT_NRD_FSR("RT_NRD_FSR", "render_pipeline.preset.rt_nrd_fsr"),
    RT_NRD_XESS("RT_NRD_XESS", "render_pipeline.preset.rt_nrd_xess"),
    ;

    public final String name;
    public final String key;

    Presets(String name, String key) {
        this.name = name;
        this.key = key;
    }
}
