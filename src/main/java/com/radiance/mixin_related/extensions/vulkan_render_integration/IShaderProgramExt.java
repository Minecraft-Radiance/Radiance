package com.radiance.mixin_related.extensions.vulkan_render_integration;

import com.mojang.blaze3d.shaders.Uniform;
import java.util.List;
import java.util.Map;

public interface IShaderProgramExt {

    List<String> radiance$getSamplerNamesValue();

    List<Uniform> radiance$getUniformsValue();

    Map<String, Object> radiance$getSamplersValue();
}
