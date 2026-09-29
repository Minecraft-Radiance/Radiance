package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
public class ClientChunkManagerMixins {

    @Inject(method = "onLightUpdate(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;)V", at = @At(value = "HEAD"),
        cancellable = true)
    public void cancelLightUpdate(LightLayer type, SectionPos pos, CallbackInfo ci) {
        ci.cancel();
    }
}
