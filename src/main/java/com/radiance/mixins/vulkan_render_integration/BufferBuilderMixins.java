package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.render.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BufferBuilder.class)
public interface BufferBuilderMixins {

    @Invoker("grow")
    void radiance$grow(int size);
}
