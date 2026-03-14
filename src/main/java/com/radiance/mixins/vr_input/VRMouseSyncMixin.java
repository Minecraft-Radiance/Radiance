package com.radiance.mixins.vr_input;

import com.radiance.client.proxy.vulkan.VRProxy;
import com.radiance.client.vr.VRMouseState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In VR keyboard+mouse mode: intercept mouse rotation so only yaw accumulates
 * into the tracking-space rotation. Pitch from mouse is discarded (HMD handles it).
 * When VR is not active or mouseTrackingEnabled is false, vanilla behavior is untouched.
 */
@Mixin(Entity.class)
public class VRMouseSyncMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void radiance$interceptMouseForVR(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!VRProxy.isSessionRunning() || !VRMouseState.mouseTrackingEnabled) return;
        if (!((Object) this instanceof ClientPlayerEntity)) return;

        // Accumulate yaw only (MC convention: cursorDeltaX * 0.15 = degrees).
        // Negate because worldOrientation's inverse is applied in C++ rendering:
        // positive mouseYaw should rotate view rightward (negative yaw in MC).
        VRMouseState.mouseYaw -= (float) (cursorDeltaX * 0.15);
        ci.cancel();
    }
}
