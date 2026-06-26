package com.radiance.mixins.blaze3d_26;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.radiance.client.blaze3d.scene.Blaze3DFrameObserver;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererFrameObserverMixin {
    @Shadow
    @Final
    private GameRenderer gameRenderer;

    @Inject(method = "render", at = @At("HEAD"))
    private void radiance$beginBlaze3DFrame(GraphicsResourceAllocator resourceAllocator,
        DeltaTracker deltaTracker, boolean renderOutline, CameraRenderState cameraState,
        Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog, Vector4f fogColor,
        boolean shouldRenderSky, CallbackInfo ci) {
        Blaze3DFrameObserver.beginLevelFrame(this.gameRenderer, deltaTracker, cameraState, modelViewMatrix,
            fogColor);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void radiance$endBlaze3DFrame(GraphicsResourceAllocator resourceAllocator,
        DeltaTracker deltaTracker, boolean renderOutline, CameraRenderState cameraState,
        Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog, Vector4f fogColor,
        boolean shouldRenderSky, CallbackInfo ci) {
        Blaze3DFrameObserver.endLevelFrame();
    }
}
