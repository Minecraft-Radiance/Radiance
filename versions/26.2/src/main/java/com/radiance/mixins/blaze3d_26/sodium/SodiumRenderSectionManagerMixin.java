package com.radiance.mixins.blaze3d_26.sodium;

import com.radiance.client.scene_import.sodium.SodiumChunkMeshBridge;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class SodiumRenderSectionManagerMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void radiance$initChunkMeshGrid(ClientLevel level, int renderDistance,
        SortBehavior sortBehavior, CallbackInfo ci) {
        SodiumChunkMeshBridge.initGrid(level, renderDistance);
    }

    @Inject(method = "prepareFrame", at = @At("HEAD"))
    private void radiance$updateChunkMeshCamera(Vector3dc cameraPosition, CallbackInfo ci) {
        SodiumChunkMeshBridge.updateCameraSection(cameraPosition);
    }

    @Inject(method = "onSectionRemoved", at = @At("HEAD"))
    private void radiance$invalidateChunkMeshSection(int x, int y, int z,
        CallbackInfo ci) {
        SodiumChunkMeshBridge.invalidateSection(x, y, z);
    }
}
