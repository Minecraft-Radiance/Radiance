package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.ChunkProxy;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltChunkStorage.class)
public class BuiltChunkStorageMixins {

    @Shadow
    public ChunkBuilder.BuiltChunk[] chunks;

    @Inject(method = "clear()V", at = @At(value = "HEAD"))
    public void clearChunkProxy(CallbackInfo ci) {
        ChunkProxy.clear();
    }

    @ModifyVariable(method = "createChunks(Lnet/minecraft/client/render/chunk/ChunkBuilder;)V", at = @At(value = "STORE"), ordinal = 0)
    private int initChunkRebuildGrid(int i) {
        ChunkProxy.init(i);
        return i;
    }

    @Inject(method = "createChunks(Lnet/minecraft/client/render/chunk/ChunkBuilder;)V", at = @At("TAIL"))
    private void enqueueInitialChunkRebuilds(CallbackInfo ci) {
        if (this.chunks == null) {
            return;
        }

        for (ChunkBuilder.BuiltChunk builtChunk : this.chunks) {
            if (builtChunk != null) {
                builtChunk.scheduleRebuild(false);
            }
        }
    }
}
