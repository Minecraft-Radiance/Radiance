package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.client.proxy.world.EntityProxy;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderLevelStageEvent.Stage.class)
public abstract class WorldRenderEventMixins {
    @WrapMethod(method = "dispatch", remap = false)
    private void captureStage(LevelRenderer renderer, Matrix4f view, Matrix4f projection,
        int ticks, Camera camera, Frustum frustum, Operation<Void> original) {
        EntityProxy.captureWorldRender(this,
            () -> original.call(renderer, view, projection, ticks, camera, frustum));
    }
}
