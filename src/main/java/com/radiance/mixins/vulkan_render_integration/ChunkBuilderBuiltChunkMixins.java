package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.BlockEntitySectionCache;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import java.util.Collection;
import java.util.stream.Collector;
import java.util.stream.Stream;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkRenderDispatcher.RenderChunk.class)
public abstract class ChunkBuilderBuiltChunkMixins implements IChunkBuilderBuiltChunkExt {

    @Unique
    private ChunkRenderDispatcher radiance$chunkBuilder;

    public ChunkRenderDispatcher radiance$getChunkBuilder() {
        return this.radiance$chunkBuilder;
    }

    @Shadow
    abstract void beginLayer(BufferBuilder buffer);

    @Shadow
    public abstract void updateGlobalBlockEntities(Collection<BlockEntity> blockEntities);

    @Override
    public void radiance$beginBufferBuilding(BufferBuilder bufferBuilder) {
        this.beginLayer(bufferBuilder);
    }

    @Override
    public void radiance$setNoCullingBlockEntities(Collection<BlockEntity> blockEntities) {
        this.updateGlobalBlockEntities(blockEntities);
    }

    @Redirect(method = "<init>",
        at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Object cancelCollect(Stream<?> stream, Collector<?, ?, ?> collector) {
        return null;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureChunkBuilder(ChunkRenderDispatcher chunkBuilder, int index, int x, int y, int z,
                                     CallbackInfo ci) {
        this.radiance$chunkBuilder = chunkBuilder;
    }

    @Inject(method = "reset()V", at = @At(value = "TAIL"))
    private void addToRebuildGridClear(CallbackInfo ci) {
        ChunkRenderDispatcher.RenderChunk self = (ChunkRenderDispatcher.RenderChunk) (Object) this;
        BlockEntitySectionCache.changed(self);
        ChunkProxy.enqueueRebuild(self);
    }

    @Inject(method = "setDirty(Z)V", at = @At(value = "TAIL"))
    private void addToRebuildGridScheduleRebuild(CallbackInfo ci) {
        ChunkRenderDispatcher.RenderChunk self = (ChunkRenderDispatcher.RenderChunk) (Object) this;
        ChunkProxy.enqueueRebuild(self);
    }

    @Inject(method = "setOrigin(III)V", at = @At(value = "HEAD"))
    private void beginNativeChunkRelocation(int x, int y, int z, CallbackInfo ci) {
        ChunkRenderDispatcher.RenderChunk self = (ChunkRenderDispatcher.RenderChunk) (Object) this;
        ChunkProxy.beginRelocation(self, x, y, z);
    }

    @Inject(method = "setOrigin(III)V", at = @At(value = "TAIL"))
    private void finishNativeChunkRelocation(int x, int y, int z, CallbackInfo ci) {
        ChunkProxy.finishRelocation((ChunkRenderDispatcher.RenderChunk) (Object) this);
    }

    @Inject(method = "releaseBuffers()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher$RenderChunk;reset()V",
            shift = At.Shift.AFTER),
        cancellable = true)
    public void cancelVertexConsumerDelete(CallbackInfo ci) {
        ci.cancel();
    }
}
