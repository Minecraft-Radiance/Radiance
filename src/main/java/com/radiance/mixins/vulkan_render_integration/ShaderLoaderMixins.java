package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.shaders.CompiledShader;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ICompiledShaderExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.ShaderProgram;
import net.minecraft.client.renderer.ShaderProgramConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderManager.class)
public class ShaderLoaderMixins {

    @Inject(method = "Lnet/minecraft/client/renderer/ShaderManager;linkProgram(Lnet/minecraft/client/renderer/ShaderProgram;Lnet/minecraft/client/renderer/ShaderProgramConfig;Lcom/mojang/blaze3d/shaders/CompiledShader;Lcom/mojang/blaze3d/shaders/CompiledShader;)Lnet/minecraft/client/renderer/CompiledShaderProgram;", at = @At("RETURN"))
    private static void captureProgramMetadata(ShaderProgram key,
        ShaderProgramConfig definition, CompiledShader vertexShader,
        CompiledShader fragmentShader, CallbackInfoReturnable<CompiledShaderProgram> cir) {
        CompiledShaderProgram shaderProgram = cir.getReturnValue();
        IShaderProgramExt ext = (IShaderProgramExt) (Object) shaderProgram;
        ext.radiance$setShaderName(key.configId().toString());
        ext.radiance$setVertexFormat(key.vertexFormat());
        ext.radiance$setVertexSource(
            ((ICompiledShaderExt) (Object) vertexShader).radiance$getResolvedSource());
        ext.radiance$setFragmentSource(
            ((ICompiledShaderExt) (Object) fragmentShader).radiance$getResolvedSource());
    }
}
