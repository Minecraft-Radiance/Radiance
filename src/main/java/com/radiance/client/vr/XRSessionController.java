package com.radiance.client.vr;

import com.radiance.client.option.Options;
//#if MC>=260200
//$$ import com.radiance.client.blaze3d.abi.VRProxy;
//$$ import net.minecraft.client.Minecraft;
//#else
import com.radiance.client.proxy.vulkan.VRProxy;
import net.minecraft.client.MinecraftClient;
//#endif

public final class XRSessionController {

    private static boolean lastTargetActive = false;
    private static boolean lastDesktopPath = true;

    private XRSessionController() {
    }

    //#if MC>=260200
    //$$ public static void tick(Minecraft client) {
    //#else
    public static void tick(MinecraftClient client) {
    //#endif
        if (client == null) {
            return;
        }

        //#if MC>=260200
        //$$ boolean inWorld = client.level != null && client.isGameLoadFinished();
        //#else
        boolean inWorld = client.world != null && client.isFinishedLoading();
        //#endif
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
