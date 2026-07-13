package com.radiance.mixins.blaze3d_26.sodium;

import com.radiance.client.scene_import.sodium.SodiumChunkBuildOutputAccess;
import com.radiance.client.scene_import.sodium.SodiumChunkMeshBridge;
import com.radiance.client.scene_import.sodium.SodiumPreparedSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkBuildOutput.class, remap = false)
public abstract class SodiumChunkBuildOutputMixin implements SodiumChunkBuildOutputAccess {
    @Unique
    private SodiumPreparedSection radiance$preparedSection;

    @Override
    public void radiance$setPreparedSection(SodiumPreparedSection section) {
        this.radiance$preparedSection = section;
    }

    @Override
    public SodiumPreparedSection radiance$takePreparedSection() {
        SodiumPreparedSection section = this.radiance$preparedSection;
        this.radiance$preparedSection = null;
        return section;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void radiance$prepareCompletedMesh(CallbackInfo ci) {
        SodiumChunkMeshBridge.prepareBuildOutput((ChunkBuildOutput) (Object) this);
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void radiance$releasePreparedMesh(CallbackInfo ci) {
        SodiumChunkMeshBridge.discardBuildOutput((ChunkBuildOutput) (Object) this);
    }
}
