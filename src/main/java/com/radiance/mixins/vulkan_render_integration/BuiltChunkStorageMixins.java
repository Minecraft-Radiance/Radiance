package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.ChunkProxy;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ViewArea.class)
public class BuiltChunkStorageMixins {

    @Shadow
    protected int sectionGridSizeY;

    @Shadow
    protected int sectionGridSizeX;

    @Shadow
    protected int sectionGridSizeZ;

    @Shadow
    protected Level level;

    @Inject(method = "Lnet/minecraft/client/renderer/ViewArea;releaseAllBuffers()V", at = @At(value = "HEAD"))
    public void clearChunkProxy(CallbackInfo ci) {
        ChunkProxy.clear();
    }

    @ModifyVariable(method = "Lnet/minecraft/client/renderer/ViewArea;createSections(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;)V", at = @At(value = "STORE"), ordinal = 0)
    private int initChunkRebuildGrid(int i) {
        ChunkProxy.setStorage((ViewArea) (Object) this);
        ChunkProxy.init(i, sectionGridSizeX, sectionGridSizeY, sectionGridSizeZ, level.getMinSectionY());
        ChunkProxy.setStorage((ViewArea) (Object) this);
        return i;
    }

    @Inject(method = "Lnet/minecraft/client/renderer/ViewArea;repositionCamera(Lnet/minecraft/core/SectionPos;)V",
        at = @At(value = "HEAD"))
    private void updateChunkStorageSectionPos(SectionPos sectionPos, CallbackInfo ci) {
        ChunkProxy.updateSectionPos(sectionPos);
    }
}
