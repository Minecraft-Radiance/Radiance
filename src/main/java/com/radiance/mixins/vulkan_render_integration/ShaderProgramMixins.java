package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.shaders.Uniform;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderInstance.class)
public abstract class ShaderProgramMixins implements IShaderProgramExt {

    @Shadow
    @Final
    private Map<String, Object> samplerMap;

    @Shadow
    @Final
    private List<String> samplerNames;

    @Shadow
    @Final
    private List<Uniform> uniforms;

    @Shadow
    @Final
    private List<Integer> samplerLocations;

    @Shadow
    @Final
    private List<Integer> uniformLocations;

    @Shadow
    @Final
    private Map<String, Uniform> uniformMap;

    @Inject(method = "updateLocations()V", at = @At("HEAD"), cancellable = true)
    private void loadReferencesWithoutOpenGL(CallbackInfo ci) {
        this.samplerLocations.clear();
        for (int i = 0; i < this.samplerNames.size(); i++) {
            this.samplerLocations.add(i);
        }

        this.uniformLocations.clear();
        this.uniformMap.clear();
        for (int i = 0; i < this.uniforms.size(); i++) {
            Uniform uniform = this.uniforms.get(i);
            this.uniformLocations.add(i);
            uniform.setLocation(i);
            this.uniformMap.put(uniform.getName(), uniform);
        }
        ci.cancel();
    }

    @Inject(method = "apply()V", at = @At("HEAD"), cancellable = true)
    private void bindWithoutOpenGL(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "clear()V", at = @At("HEAD"), cancellable = true)
    private void unbindWithoutOpenGL(CallbackInfo ci) {
        ci.cancel();
    }

    @Override
    public List<String> radiance$getSamplerNamesValue() {
        return samplerNames;
    }

    @Override
    public List<Uniform> radiance$getUniformsValue() {
        return uniforms;
    }

    @Override
    public Map<String, Object> radiance$getSamplersValue() {
        return samplerMap;
    }
}
