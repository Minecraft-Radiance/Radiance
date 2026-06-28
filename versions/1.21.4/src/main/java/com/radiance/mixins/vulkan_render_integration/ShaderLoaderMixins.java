package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.ICompiledShaderExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import net.minecraft.client.gl.CompiledShader;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramDefinition;
import net.minecraft.client.gl.ShaderProgramKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderLoader.class)
public class ShaderLoaderMixins {

    @Inject(method = "createProgram", at = @At("RETURN"))
    private static void captureProgramMetadata(ShaderProgramKey key,
        ShaderProgramDefinition definition, CompiledShader vertexShader,
        CompiledShader fragmentShader, CallbackInfoReturnable<ShaderProgram> cir) {
        ShaderProgram shaderProgram = cir.getReturnValue();
        IShaderProgramExt ext = (IShaderProgramExt) (Object) shaderProgram;
        ext.radiance$setShaderName(key.configId().toString());
        ext.radiance$setVertexFormat(key.vertexFormat());
        ext.radiance$setVertexSource(
            ((ICompiledShaderExt) (Object) vertexShader).radiance$getResolvedSource());
        ext.radiance$setFragmentSource(
            ((ICompiledShaderExt) (Object) fragmentShader).radiance$getResolvedSource());
    }
}
