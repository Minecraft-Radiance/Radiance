package com.radiance.mixin_related.extensions.vulkan_render_integration;

import java.util.List;
import java.util.Map;
import net.minecraft.client.gl.GlUniform;

public interface IShaderProgramExt {

    List<String> radiance$getSamplerNamesValue();

    List<GlUniform> radiance$getUniformsValue();

    Map<String, Object> radiance$getSamplersValue();
}
