package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.proxy.world.EntityProxy;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ForgeHooksClient.class)
public abstract class WorldRenderEventMixins {
    @WrapMethod(method = "dispatchRenderStage(Lnet/minecraftforge/client/event/RenderLevelStageEvent$Stage;Lnet/minecraft/client/renderer/LevelRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V", remap = false)
    private static void captureStage(RenderLevelStageEvent.Stage stage, LevelRenderer renderer,
        PoseStack poses, Matrix4f projection, int ticks, Camera camera, Frustum frustum, Operation<Void> original) {
        EntityProxy.captureWorldRender(stage,
            () -> original.call(stage, renderer, poses, projection, ticks, camera, frustum));
    }
}
