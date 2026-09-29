package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IBuiltChunkStorageExt;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltChunkStorage.class)
public abstract class BuiltChunkStorageMixins implements IBuiltChunkStorageExt {

    @Shadow
    protected int sizeY;

    @Shadow
    protected int sizeX;

    @Shadow
    protected int sizeZ;

    @Shadow
    protected World world;

    @Shadow
    public ChunkBuilder.BuiltChunk[] chunks;

    @Shadow
    protected abstract int getChunkIndex(int x, int y, int z);

    @Inject(method = "clear()V", at = @At(value = "HEAD"))
    public void clearChunkProxy(CallbackInfo ci) {
        ChunkProxy.clear();
    }

    @ModifyVariable(method = "createChunks(Lnet/minecraft/client/render/chunk/ChunkBuilder;)V", at = @At(value = "STORE"), ordinal = 0)
    private int initChunkRebuildGrid(int i) {
        ChunkProxy.setStorage((BuiltChunkStorage) (Object) this);
        ChunkProxy.init(i, sizeX, sizeY, sizeZ, world.getBottomSectionCoord());
        ChunkProxy.setStorage((BuiltChunkStorage) (Object) this);
        return i;
    }

    @Override
    public void radiance$updateCameraPosition(ChunkSectionPos sectionPos) {
        ChunkProxy.setStorage((BuiltChunkStorage) (Object) this);
        ChunkProxy.updateSectionPos(sectionPos);
        int viewDistance = (this.sizeX - 1) / 2;
        for (int x = 0; x < this.sizeX; x++) {
            int minSectionX = sectionPos.getSectionX() - viewDistance;
            int sectionX = minSectionX + Math.floorMod(x - minSectionX, this.sizeX);

            for (int z = 0; z < this.sizeZ; z++) {
                int minSectionZ = sectionPos.getSectionZ() - viewDistance;
                int sectionZ = minSectionZ + Math.floorMod(z - minSectionZ, this.sizeZ);

                for (int y = 0; y < this.sizeY; y++) {
                    int sectionY = this.world.getBottomSectionCoord() + y;
                    ChunkBuilder.BuiltChunk builtChunk = this.chunks[this.getChunkIndex(x, y, z)];
                    int originX = ChunkSectionPos.getBlockCoord(sectionX);
                    int originY = ChunkSectionPos.getBlockCoord(sectionY);
                    int originZ = ChunkSectionPos.getBlockCoord(sectionZ);
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
