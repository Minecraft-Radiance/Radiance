package com.radiance.client.vr;

import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.VRProxy;
import net.minecraft.client.MinecraftClient;

public final class XRSessionController {

    private static boolean lastTargetActive = false;
    private static boolean lastDesktopPath = true;

    private XRSessionController() {
    }

    public static void tick(MinecraftClient client) {
        if (client == null) {
            return;
        }

        boolean inWorld = client.world != null && client.isFinishedLoading();
        boolean targetActive = Options.vrEnabled && inWorld;

        // Keep runtime prepared according to user setting, but only run XR session in-world.
        VRProxy.setEnabled(Options.vrEnabled);

        if (targetActive && !lastTargetActive) {
            lastTargetActive = VRProxy.startXRSession();
        } else if (!targetActive && lastTargetActive) {
            VRProxy.stopXRSession();
            lastTargetActive = false;
        } else if (!targetActive) {
            lastTargetActive = false;
        }

        boolean desktopPath = VRProxy.isDesktopPathActive();
        if (desktopPath != lastDesktopPath) {
            if (desktopPath) {
                VRMouseState.enterDesktopPath();
            } else {
                VRMouseState.enterXRPath();
            }
            lastDesktopPath = desktopPath;
        }
    }
}
