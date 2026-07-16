package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.ShaderProxy;
import com.radiance.client.shader.ShaderDefinition;
import com.radiance.client.shader.ShaderRegistry;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferRenderer.class)
public class BufferRendererMixins {

    @Inject(method = "drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER, remap = false),
        cancellable = true)
    private static void rewriteDrawWithGlobalProgram(BuiltBuffer buffer, CallbackInfo ci) {
        ShaderProgram shaderProgram = RenderSystem.getShader();
        if (shaderProgram == null) {
            buffer.close();
            throw new IllegalStateException(
                "No active shader for shader draw: " + buffer.getDrawParameters()
                    .format());
        }
        ShaderProxy.syncState(shaderProgram, buffer.getDrawParameters().mode());
        ShaderDefinition shader = ShaderRegistry.getOrCreate(shaderProgram);
        BufferProxy.VertexIndexBufferHandle handle = BufferProxy.createAndUploadVertexIndexBuffer(
            buffer);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ShaderProxy.UniformHandle uniform = ShaderProxy.createUniform(shader, shaderProgram,
                stack);
            ShaderProxy.draw(handle, shader.nativeId(),
                buffer.getDrawParameters()
                    .indexCount(),
                Constants.IndexTypes.getValue(buffer.getDrawParameters()
                    .indexType()),
                uniform.addr(),
                uniform.size());
        }

        buffer.close();

        ci.cancel();
    }
}
