package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IBuiltChunkStorageExt;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ViewArea.class)
public abstract class BuiltChunkStorageMixins implements IBuiltChunkStorageExt {

    @Shadow
    protected int chunkGridSizeY;

    @Shadow
    protected int chunkGridSizeX;

    @Shadow
    protected int chunkGridSizeZ;

    @Shadow
    protected Level level;

    @Shadow
    public ChunkRenderDispatcher.RenderChunk[] chunks;

    @Shadow
    protected abstract int getChunkIndex(int x, int y, int z);

    @Inject(method = "releaseAllBuffers()V", at = @At(value = "HEAD"))
    public void clearChunkProxy(CallbackInfo ci) {
        ChunkProxy.clear();
    }

    @ModifyVariable(method = "createChunks(Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;)V", at = @At(value = "STORE"), ordinal = 0)
    private int initChunkRebuildGrid(int i) {
        ChunkProxy.setStorage((ViewArea) (Object) this);
        ChunkProxy.init(i, chunkGridSizeX, chunkGridSizeY, chunkGridSizeZ, level.getMinSection());
        ChunkProxy.setStorage((ViewArea) (Object) this);
        return i;
    }

    @Override
    public void radiance$updateCameraPosition(SectionPos sectionPos) {
        ChunkProxy.setStorage((ViewArea) (Object) this);
        ChunkProxy.updateSectionPos(sectionPos);
        int viewDistance = (this.chunkGridSizeX - 1) / 2;
        for (int x = 0; x < this.chunkGridSizeX; x++) {
            int minSectionX = sectionPos.x() - viewDistance;
            int sectionX = minSectionX + Math.floorMod(x - minSectionX, this.chunkGridSizeX);

            for (int z = 0; z < this.chunkGridSizeZ; z++) {
                int minSectionZ = sectionPos.z() - viewDistance;
                int sectionZ = minSectionZ + Math.floorMod(z - minSectionZ, this.chunkGridSizeZ);

                for (int y = 0; y < this.chunkGridSizeY; y++) {
                    int sectionY = this.level.getMinSection() + y;
                    ChunkRenderDispatcher.RenderChunk builtChunk = this.chunks[this.getChunkIndex(x, y, z)];
                    int originX = SectionPos.sectionToBlockCoord(sectionX);
                    int originY = SectionPos.sectionToBlockCoord(sectionY);
                    int originZ = SectionPos.sectionToBlockCoord(sectionZ);
                    BlockPos origin = builtChunk.getOrigin();
                    if (originX != origin.getX() || originY != origin.getY()
                        || originZ != origin.getZ()) {
                        builtChunk.setOrigin(originX, originY, originZ);
                    }
                }
            }
        }
    }
}
