package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderProgram.class)
public abstract class ShaderProgramMixins implements IShaderProgramExt {

    @Shadow
    @Final
    private Map<String, Object> samplers;

    @Shadow
    @Final
    private List<String> samplerNames;

    @Shadow
    @Final
    private List<GlUniform> uniforms;

    @Shadow
    @Final
    private List<Integer> loadedSamplerIds;

    @Shadow
    @Final
    private List<Integer> loadedUniformIds;

    @Shadow
    @Final
    private Map<String, GlUniform> loadedUniforms;

    @Inject(method = "loadReferences", at = @At("HEAD"), cancellable = true)
    private void loadReferencesWithoutOpenGL(CallbackInfo ci) {
        this.loadedSamplerIds.clear();
        for (int i = 0; i < this.samplerNames.size(); i++) {
            this.loadedSamplerIds.add(i);
        }

        this.loadedUniformIds.clear();
        this.loadedUniforms.clear();
        for (int i = 0; i < this.uniforms.size(); i++) {
            GlUniform uniform = this.uniforms.get(i);
            this.loadedUniformIds.add(i);
            uniform.setLocation(i);
            this.loadedUniforms.put(uniform.getName(), uniform);
        }
        ci.cancel();
    }

    @Inject(method = "bind", at = @At("HEAD"), cancellable = true)
    private void bindWithoutOpenGL(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "unbind", at = @At("HEAD"), cancellable = true)
    private void unbindWithoutOpenGL(CallbackInfo ci) {
        ci.cancel();
    }

    @Override
    public List<String> radiance$getSamplerNamesValue() {
        return samplerNames;
    }

    @Override
    public List<GlUniform> radiance$getUniformsValue() {
        return uniforms;
    }

    @Override
    public Map<String, Object> radiance$getSamplersValue() {
        return samplers;
    }
}
