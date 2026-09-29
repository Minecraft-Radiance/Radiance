package com.radiance.mixins.mod_support.flywheel;

import com.radiance.client.mod_support.flywheel.FlywheelSupport;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldLifecycleMixins {
    @Inject(method = "resetChunkColor", at = @At("TAIL"))
    private void radiance$chunkReady(ChunkPos pos, CallbackInfo ci) {
        FlywheelSupport.chunkReady((ClientWorld) (Object) this, pos);
    }

    @Inject(method = "unloadBlockEntities", at = @At("TAIL"))
    private void radiance$chunkUnloaded(WorldChunk chunk, CallbackInfo ci) {
        FlywheelSupport.chunkUnloaded((ClientWorld) (Object) this, chunk.getPos());
    }
}
