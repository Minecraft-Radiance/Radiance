package com.radiance.client.blaze3d.abi;

public final class VRProxy {

    private static boolean enabled;
    private static float renderScale = 1.0f;
    private static float ipd = 0.063f;
    private static float worldScale = 1.0f;
    private static final float[] WORLD_POSITION = new float[] {0.0f, 0.0f, 0.0f};
    private static final float[] WORLD_ORIENTATION = new float[] {0.0f, 0.0f, 0.0f, 1.0f};

    private VRProxy() {
    }

    public static void setEnabled(boolean enabled) {
        VRProxy.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean startXRSession() {
        return false;
    }

    public static void stopXRSession() {
    }

    public static boolean isSessionRunning() {
        return false;
    }

    public static boolean isDesktopPathActive() {
        return true;
    }

    public static int getEyeCount() {
        return 1;
    }

    public static int getEyeRenderWidth() {
        return 0;
    }

    public static int getEyeRenderHeight() {
        return 0;
    }

    public static int[] getRecommendedResolution() {
        return new int[] {0, 0};
    }

    public static float[] getPerformanceStats() {
        return new float[] {0.0f, 0.0f, 0.0f, 0.0f, 16.67f, 0.0f, 0.0f, 0.0f};
    }

    public static void setRenderScale(float renderScale) {
        VRProxy.renderScale = Math.max(0.1f, Math.min(2.0f, renderScale));
    }

    public static float getRenderScale() {
        return renderScale;
    }

    public static void setIPD(float ipd) {
        VRProxy.ipd = Math.max(0.0f, ipd);
    }

    public static float getIPD() {
        return ipd;
    }

    public static void setWorldScale(float worldScale) {
        VRProxy.worldScale = Math.max(0.01f, worldScale);
    }

    public static float getWorldScale() {
        return worldScale;
    }

    public static void nativeSetWorldOrientation(float qx, float qy, float qz, float qw) {
        WORLD_ORIENTATION[0] = qx;
        WORLD_ORIENTATION[1] = qy;
        WORLD_ORIENTATION[2] = qz;
        WORLD_ORIENTATION[3] = qw;
    }

    public static float[] getWorldOrientation() {
        return WORLD_ORIENTATION.clone();
    }

    public static float[] getHeadPose() {
        return new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f};
    }

    public static void setWorldPosition(float x, float y, float z) {
        WORLD_POSITION[0] = x;
        WORLD_POSITION[1] = y;
        WORLD_POSITION[2] = z;
    }

    public static float[] getWorldPosition() {
        return WORLD_POSITION.clone();
    }
}
