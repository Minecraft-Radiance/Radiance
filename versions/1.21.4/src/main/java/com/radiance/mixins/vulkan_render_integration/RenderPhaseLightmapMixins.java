package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderPhase.Lightmap.class)
public class RenderPhaseLightmapMixins extends RenderPhaseMixins {

    @Inject(method = "<init>(Z)V", at = @At(value = "TAIL"))
    public void resetActionToDoNothing(boolean lightmap, CallbackInfo ci) {
        if (!lightmap) {
            setBeginAction(() -> {
            });
            setEndAction(() -> {
            });
            return;
        }

        setBeginAction(() -> MinecraftClient.getInstance()
            .gameRenderer
            .getLightmapTextureManager()
            .enable());
        setEndAction(() -> MinecraftClient.getInstance()
            .gameRenderer
            .getLightmapTextureManager()
            .disable());
    }
}
