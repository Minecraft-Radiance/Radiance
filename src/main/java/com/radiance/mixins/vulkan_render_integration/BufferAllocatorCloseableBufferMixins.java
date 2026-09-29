package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ICloseableBufferExt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ByteBufferBuilder.Result.class)
public abstract class BufferAllocatorCloseableBufferMixins implements ICloseableBufferExt {

    @Shadow
    @Final
    private ByteBufferBuilder this$0;

    @Override
    public ByteBufferBuilder radiance$getOwnerAllocator() {
        return this$0;
    }
}
