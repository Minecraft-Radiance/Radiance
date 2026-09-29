package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SectionRenderDispatcher.class)
public class ChunkBuilderMixins implements IChunkBuilderExt {

    @Final
    @Shadow
    SectionCompiler sectionCompiler;

    @Final
    @Shadow
    SectionBufferBuilderPack fixedBuffers;

    @Shadow
    ClientLevel level;

    @Shadow
    LevelRenderer renderer;

    @Override
    public SectionCompiler radiance$getSectionBuilder() {
        return sectionCompiler;
    }

    @Override
    public ClientLevel radiance$getWorld() {
        return level;
    }

    @Override
    public LevelRenderer radiance$getWorldRenderer() {
        return renderer;
    }

    @Override
    public SectionBufferBuilderPack radiance$getBuffers() {
        return fixedBuffers;
    }
}
