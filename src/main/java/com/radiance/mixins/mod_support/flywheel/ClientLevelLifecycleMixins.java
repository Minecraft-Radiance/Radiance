package com.radiance.mixins.mod_support.flywheel;

import com.radiance.client.mod_support.flywheel.FlywheelSupport;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelLifecycleMixins {
    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void radiance$chunkReady(ChunkPos pos, CallbackInfo ci) {
        FlywheelSupport.chunkReady((ClientLevel) (Object) this, pos);
    }

    @Inject(method = "unload", at = @At("TAIL"))
    private void radiance$chunkUnloaded(LevelChunk chunk, CallbackInfo ci) {
        FlywheelSupport.chunkUnloaded((ClientLevel) (Object) this, chunk.getPos());
    }
}
