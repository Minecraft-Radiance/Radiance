package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderStageExt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gl.GlImportProcessor;
import net.minecraft.client.gl.ShaderProgramSetupView;
import net.minecraft.client.gl.ShaderStage;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderStage.class)
public abstract class ShaderStageMixins implements IShaderStageExt {

    @Unique
    private static final AtomicInteger NEXT_VIRTUAL_SHADER_ID = new AtomicInteger(1);
    @Unique
    private static final Map<Integer, String> RESOLVED_SOURCES = new ConcurrentHashMap<>();

    @Shadow
    @Final
    private ShaderStage.Type type;

    @Shadow
    @Final
    private String name;

    @Shadow
    private int glRef;

    @Unique
    private String resolvedSource;

    @Inject(method = "load", at = @At("HEAD"), cancellable = true)
    private static void loadWithoutOpenGL(ShaderStage.Type type, String name, InputStream stream,
        String domain, GlImportProcessor loader, CallbackInfoReturnable<Integer> cir)
        throws IOException {
        String source = IOUtils.toString(stream, StandardCharsets.UTF_8);
        if (source == null) {
            throw new IOException("Could not load program " + type.getName());
        }
        int shaderId = NEXT_VIRTUAL_SHADER_ID.getAndIncrement();
        RESOLVED_SOURCES.put(shaderId, String.join("", loader.readSource(source)));
        cir.setReturnValue(shaderId);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void captureResolvedSource(ShaderStage.Type type, int glRef, String name,
        CallbackInfo ci) {
        this.resolvedSource = RESOLVED_SOURCES.remove(glRef);
    }

    @Inject(method = "attachTo", at = @At("HEAD"), cancellable = true)
    private void attachWithoutOpenGL(ShaderProgramSetupView program, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "release", at = @At("HEAD"), cancellable = true)
    private void releaseWithoutOpenGL(CallbackInfo ci) {
        if (this.glRef != -1) {
            this.type.getLoadedShaders()
                .remove(this.name);
            this.glRef = -1;
        }
        ci.cancel();
    }

    @Override
    public String radiance$getResolvedSource() {
        return this.resolvedSource;
    }
}
