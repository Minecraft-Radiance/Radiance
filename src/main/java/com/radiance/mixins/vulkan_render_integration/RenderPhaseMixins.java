package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RenderStateShard.class)
public class RenderPhaseMixins {

    @Mutable
    @Final
    @Shadow
    private Runnable setupState;

    @Mutable
    @Final
    @Shadow
    private Runnable clearState;

    @Unique
    public void setBeginAction(Runnable setupState) {
        this.setupState = setupState;
    }

    @Unique
    public void setEndAction(Runnable clearState) {
        this.clearState = clearState;
    }
}
