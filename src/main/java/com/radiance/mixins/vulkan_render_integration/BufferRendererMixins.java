package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.ShaderProxy;
import com.radiance.client.shader.ShaderDefinition;
import com.radiance.client.shader.ShaderRegistry;
import net.minecraft.client.renderer.CompiledShaderProgram;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferUploader.class)
public class BufferRendererMixins {

    @Inject(method = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER, remap = false),
        cancellable = true)
    private static void rewriteDrawWithGlobalProgram(MeshData buffer, CallbackInfo ci) {
        CompiledShaderProgram shaderProgram = RenderSystem.getShader();
        if (shaderProgram == null) {
            buffer.close();
            throw new IllegalStateException(
                "No active shader for shader draw: " + buffer.drawState()
                    .format());
        }
        ShaderProxy.syncState(shaderProgram, buffer.drawState().mode());
        ShaderDefinition shader = ShaderRegistry.getOrCreate(shaderProgram);
        BufferProxy.VertexIndexBufferHandle handle = BufferProxy.createAndUploadVertexIndexBuffer(
            buffer);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ShaderProxy.UniformHandle uniform = ShaderProxy.createUniform(shader, shaderProgram,
                stack);
            ShaderProxy.draw(handle, shader.nativeId(),
                buffer.drawState()
                    .indexCount(),
                Constants.IndexTypes.getValue(buffer.drawState()
                    .indexType()),
                uniform.address(),
                uniform.sizeBytes());
        }

        buffer.close();

        ci.cancel();
    }
}
