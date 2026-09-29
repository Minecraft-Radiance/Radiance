package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.BlockEntitySectionCache;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import java.util.stream.Collector;
import java.util.stream.Stream;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public class ChunkBuilderBuiltChunkMixins implements IChunkBuilderBuiltChunkExt {

    @Shadow
    @Final
    SectionRenderDispatcher this$0;

    @Unique
    public SectionRenderDispatcher radiance$getChunkBuilder() {
        return this$0;
    }

    @Redirect(method = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;<init>(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;IJ)V",
        at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Object cancelCollect(Stream<?> stream, Collector<?, ?, ?> collector) {
        return null;
    }

    @Inject(method = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;reset()V", at = @At(value = "TAIL"))
    private void addToRebuildGridClear(CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self = (SectionRenderDispatcher.RenderSection) (Object) this;
        BlockEntitySectionCache.changed(self);
        ChunkProxy.enqueueRebuild(self);
    }

    @Inject(method = "setCompiled(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$CompiledSection;)V",
        at = @At("TAIL"))
    private void updateBlockEntitySections(SectionRenderDispatcher.CompiledSection compiled, CallbackInfo ci) {
        BlockEntitySectionCache.changed((SectionRenderDispatcher.RenderSection) (Object) this);
    }

    @Inject(method = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;setDirty(Z)V", at = @At(value = "TAIL"))
    private void addToRebuildGridScheduleRebuild(CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self = (SectionRenderDispatcher.RenderSection) (Object) this;
        ChunkProxy.enqueueRebuild(self);
    }

    @Inject(method = "setSectionNode(J)V", at = @At(value = "HEAD"))
    private void beginNativeChunkRelocation(long sectionPos, CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self = (SectionRenderDispatcher.RenderSection) (Object) this;
        ChunkProxy.beginRelocation(self, net.minecraft.core.SectionPos.x(sectionPos) << 4,
            net.minecraft.core.SectionPos.y(sectionPos) << 4,
            net.minecraft.core.SectionPos.z(sectionPos) << 4);
    }

    @Inject(method = "setSectionNode(J)V", at = @At(value = "TAIL"))
    private void finishNativeChunkRelocation(long sectionPos, CallbackInfo ci) {
        ChunkProxy.finishRelocation((SectionRenderDispatcher.RenderSection) (Object) this);
    }

    @Inject(method = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;releaseBuffers()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;reset()V",
            shift = At.Shift.AFTER),
        cancellable = true)
    public void cancelVertexConsumerDelete(CallbackInfo ci) {
        ci.cancel();
    }
}
