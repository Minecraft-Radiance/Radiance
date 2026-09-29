package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.ICloseableBufferExt;
import net.minecraft.client.util.BufferAllocator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BufferAllocator.CloseableBuffer.class)
public abstract class BufferAllocatorCloseableBufferMixins implements ICloseableBufferExt {

    @Shadow
    @Final
    private BufferAllocator field_52088;

    @Override
    public BufferAllocator radiance$getOwnerAllocator() {
        return field_52088;
    }
}
