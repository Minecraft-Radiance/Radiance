package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkManager.class)
public class ClientChunkManagerMixins {

    @Inject(method = "onLightUpdate(Lnet/minecraft/world/LightType;Lnet/minecraft/util/math/ChunkSectionPos;)V", at = @At(value = "HEAD"),
        cancellable = true)
    public void cancelLightUpdate(LightType type, ChunkSectionPos pos, CallbackInfo ci) {
        ci.cancel();
    }
}
