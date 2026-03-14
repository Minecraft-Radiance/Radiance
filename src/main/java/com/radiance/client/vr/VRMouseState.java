package com.radiance.client.vr;

import com.radiance.client.proxy.vulkan.VRProxy;

/**
 * Tracks the accumulated mouse yaw for keyboard+mouse VR mode.
 * <p>
 * In VR, the mouse horizontal movement rotates the tracking space (yaw only).
 * Mouse vertical movement (pitch) is ignored — HMD physical tilt handles pitch.
 * The player's facing direction is set from HMD orientation + accumulated mouse yaw.
 */
public final class VRMouseState {

    /** Whether mouse movement controls the VR tracking space orientation. */
    public static boolean mouseTrackingEnabled = true;

    /** Accumulated mouse yaw in degrees (MC convention: negative = right). */
    public static float mouseYaw = 0.0f;

    private VRMouseState() {}

    /**
     * Called once per tick when VR is active.
     * Sets worldOrientation from mouseYaw and syncs player facing from HMD.
     */
    public static void syncPerTick(net.minecraft.client.MinecraftClient client) {
        if (!VRProxy.isSessionRunning() || !mouseTrackingEnabled || client.player == null) return;

        // 1. Push mouseYaw as worldOrientation quaternion to C++.
        //    worldOrientation = rotateY(mouseYaw_rad)
        float yawRad = (float) Math.toRadians(mouseYaw);
        float halfYaw = yawRad * 0.5f;
        float sinHalf = (float) Math.sin(halfYaw);
        float cosHalf = (float) Math.cos(halfYaw);
        // Quaternion for Y rotation: (x=0, y=sin(θ/2), z=0, w=cos(θ/2))
        VRProxy.nativeSetWorldOrientation(0, sinHalf, 0, cosHalf);

        // 2. Sync player facing direction from HMD + mouseYaw.
        //    Extract forward from HMD quaternion, rotate by mouseYaw,
        //    then compute MC yaw/pitch.
        float[] headPose = VRProxy.getHeadPose();
        if (headPose == null || headPose.length < 7 || headPose[6] == 0) return;

        float qx = headPose[3], qy = headPose[4];
        float qz = headPose[5], qw = headPose[6];

        // Forward = quaternion * (0, 0, -1)  (tracking-space forward)
        float fx = -2.0f * (qx * qz + qw * qy);
        float fy = -2.0f * (qy * qz - qw * qx);
        float fz = 2.0f * (qx * qx + qy * qy) - 1.0f;

        // Rotate to world space by mouseYaw around Y
        float cosR = (float) Math.cos(yawRad);
        float sinR = (float) Math.sin(yawRad);
        float wx = fx * cosR + fz * sinR;
        float wz = -fx * sinR + fz * cosR;

        // Set player facing (MC yaw/pitch conventions)
        client.player.setYaw((float) Math.toDegrees(Math.atan2(-wx, wz)));
        client.player.setPitch((float) Math.toDegrees(
            Math.asin(Math.max(-1.0, Math.min(1.0, -fy)))));
    }

    /** Enter desktop path: clear tracking-space offsets and restore vanilla mouse behavior. */
    public static void enterDesktopPath() {
        mouseTrackingEnabled = false;
        mouseYaw = 0.0f;
        VRProxy.nativeSetWorldOrientation(0.0f, 0.0f, 0.0f, 1.0f);
        VRProxy.setWorldPosition(0.0f, 0.0f, 0.0f);
    }

    /** Enter XR path: enable mouse-based tracking-space yaw control again. */
    public static void enterXRPath() {
        mouseTrackingEnabled = true;
    }
}
