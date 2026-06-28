package com.radiance.mixin_related.extensions.vulkan_render_integration;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public interface IGlUniformExt {

    int radiance$getDataTypeValue();

    int radiance$getCountValue();

    IntBuffer radiance$getIntDataValue();

    FloatBuffer radiance$getFloatDataValue();
}
