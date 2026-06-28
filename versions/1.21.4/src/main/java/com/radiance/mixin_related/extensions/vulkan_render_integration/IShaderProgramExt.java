package com.radiance.mixin_related.extensions.vulkan_render_integration;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.render.VertexFormat;

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

    List<GlUniform> radiance$getUniformsValue();

    Object2IntMap<String> radiance$getSamplerTexturesValue();
}
