package com.radiance.mixins.blaze3d_26.sodium;

import com.radiance.client.scene_import.sodium.SodiumChunkMeshBridge;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderSection.class, remap = false)
public abstract class SodiumRenderSectionMixin {
    @Inject(method = "retrievePendingBuildOutput", at = @At("RETURN"))
    private void radiance$submitAcceptedMesh(
        CallbackInfoReturnable<ChunkBuildOutput> cir) {
        SodiumChunkMeshBridge.submitAcceptedBuildOutput(cir.getReturnValue());
    }
}
