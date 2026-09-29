package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.proxy.world.EntityProxy;
import net.minecraft.client.Camera;
import net.minecraft.world.level.Level;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Group;

@Mixin(ClientHooks.class)
public abstract class WorldRenderEventMixins {
    @Group(name = "radianceWorldStage", min = 1, max = 1)
    @WrapMethod(method = "dispatchRenderStage(Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent$Stage;Lnet/minecraft/world/level/Level;Lnet/minecraft/client/renderer/LevelRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V", remap = false, require = 0)
    private static void captureStage(RenderLevelStageEvent.Stage stage, Level level, LevelRenderer renderer,
        PoseStack poses, Matrix4f view, Matrix4f projection, int ticks, Camera camera, Frustum frustum, Operation<Void> original) {
        EntityProxy.captureWorldRender(stage,
            () -> original.call(stage, level, renderer, poses, view, projection, ticks, camera, frustum));
    }

    @Group(name = "radianceWorldStage", min = 1, max = 1)
    @WrapMethod(method = "dispatchRenderStage(Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent$Stage;Lnet/minecraft/client/renderer/LevelRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V", remap = false, require = 0)
    private static void captureLegacyStage(RenderLevelStageEvent.Stage stage, LevelRenderer renderer,
        PoseStack poses, Matrix4f view, Matrix4f projection, int ticks, Camera camera, Frustum frustum, Operation<Void> original) {
        EntityProxy.captureWorldRender(stage,
            () -> original.call(stage, renderer, poses, view, projection, ticks, camera, frustum));
    }
}
