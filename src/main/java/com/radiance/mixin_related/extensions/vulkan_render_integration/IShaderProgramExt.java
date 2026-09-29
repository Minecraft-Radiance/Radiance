package com.radiance.mixin_related.extensions.vulkan_render_integration;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;

public interface IShaderProgramExt {

    String radiance$getShaderName();

    void radiance$setShaderName(String shaderName);

    VertexFormat radiance$getVertexFormat();

    void radiance$setVertexFormat(VertexFormat vertexFormat);

    String radiance$getVertexSource();

    void radiance$setVertexSource(String vertexSource);

    String radiance$getFragmentSource();

    void radiance$setFragmentSource(String fragmentSource);

    List<String> radiance$getSamplerNamesValue();

    List<Uniform> radiance$getUniformsValue();

    Object2IntMap<String> radiance$getSamplerTexturesValue();
}
