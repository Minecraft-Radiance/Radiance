package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.Shader;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderStageExt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Program.class)
public abstract class ShaderStageMixins implements IShaderStageExt {

    @Unique
    private static final AtomicInteger NEXT_VIRTUAL_SHADER_ID = new AtomicInteger(1);
    @Unique
    private static final Map<Integer, String> RESOLVED_SOURCES = new ConcurrentHashMap<>();

    @Shadow
    @Final
    private Program.Type type;

    @Shadow
    @Final
    private String name;

    @Shadow
    private int id;

    @Unique
    private String resolvedSource;

    @Inject(method = "compileShaderInternal(Lcom/mojang/blaze3d/shaders/Program$Type;Ljava/lang/String;Ljava/io/InputStream;Ljava/lang/String;Lcom/mojang/blaze3d/preprocessor/GlslPreprocessor;)I", at = @At("HEAD"), cancellable = true)
    private static void loadWithoutOpenGL(Program.Type type, String name, InputStream stream,
        String domain, GlslPreprocessor loader, CallbackInfoReturnable<Integer> cir)
        throws IOException {
        String source = IOUtils.toString(stream, StandardCharsets.UTF_8);
        if (source == null) {
            throw new IOException("Could not load program " + type.getName());
        }
        int shaderId = NEXT_VIRTUAL_SHADER_ID.getAndIncrement();
        RESOLVED_SOURCES.put(shaderId, String.join("", loader.process(source)));
        cir.setReturnValue(shaderId);
    }

    @Inject(method = "<init>(Lcom/mojang/blaze3d/shaders/Program$Type;ILjava/lang/String;)V", at = @At("TAIL"))
    private void captureResolvedSource(Program.Type type, int glRef, String name,
        CallbackInfo ci) {
        this.resolvedSource = RESOLVED_SOURCES.remove(glRef);
    }

    @Inject(method = "attachToShader(Lcom/mojang/blaze3d/shaders/Shader;)V", at = @At("HEAD"), cancellable = true)
    private void attachWithoutOpenGL(Shader program, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "close()V", at = @At("HEAD"), cancellable = true)
    private void releaseWithoutOpenGL(CallbackInfo ci) {
        if (this.id != -1) {
            this.type.getPrograms()
                .remove(this.name);
            this.id = -1;
        }
        ci.cancel();
    }

    @Override
    public String radiance$getResolvedSource() {
        return this.resolvedSource;
    }
}
