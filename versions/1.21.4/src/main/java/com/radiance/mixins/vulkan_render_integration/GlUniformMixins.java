package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IGlUniformExt;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.gl.GlUniform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GlUniform.class)
public abstract class GlUniformMixins implements IGlUniformExt {

    @Shadow
    @Final
    private int count;

    @Shadow
    @Final
    private int dataType;

    @Shadow
    @Final
    private IntBuffer intData;

    @Shadow
    @Final
    private FloatBuffer floatData;

    @Override
    public int radiance$getDataTypeValue() {
        return this.dataType;
    }

    @Override
    public int radiance$getCountValue() {
        return this.count;
    }

    @Override
    public IntBuffer radiance$getIntDataValue() {
        return this.intData;
    }

    @Override
    public FloatBuffer radiance$getFloatDataValue() {
        return this.floatData;
    }
}
