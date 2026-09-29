package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IGlUniformExt;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.gl.GlUniform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(method = "bindAttribLocation", at = @At("HEAD"), cancellable = true)
    private static void bindAttribLocationWithoutOpenGL(int program, int index, CharSequence name,
        CallbackInfo ci) {
        ci.cancel();
    }

    @Override
    public int radiance$getDataTypeValue() {
        return dataType;
    }

    @Override
    public int radiance$getCountValue() {
        return count;
    }

    @Override
    public IntBuffer radiance$getIntDataValue() {
        return intData;
    }

    @Override
    public FloatBuffer radiance$getFloatDataValue() {
        return floatData;
    }
}
