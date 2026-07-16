package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RenderPhase.class)
public class RenderPhaseMixins {

    @Mutable
    @Final
    @Shadow
    private Runnable beginAction;

    @Mutable
    @Final
    @Shadow
    private Runnable endAction;

    @Unique
    public void setBeginAction(Runnable beginAction) {
        this.beginAction = beginAction;
    }

    @Unique
    public void setEndAction(Runnable endAction) {
        this.endAction = endAction;
    }
}
