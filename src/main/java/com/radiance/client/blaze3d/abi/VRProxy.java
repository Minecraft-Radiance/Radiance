package com.radiance.client.blaze3d.abi;

public final class VRProxy {
    private VRProxy() {
    }

    public static native void nativeSetEnabled(boolean enabled);

    public static native boolean nativeStartXRSession();

    public static native void nativeStopXRSession();

    public static native void nativeSetRenderScale(float renderScale);

    public static native void nativeSetIPD(float ipd);

    public static native void nativeSetWorldScale(float worldScale);

    public static native void nativeSetWorldOrientation(float qx, float qy, float qz, float qw);

    public static native void nativeSetWorldPosition(float x, float y, float z);

    public static native float[] nativeGetWorldPosition();

    public static native void nativeRecenter();

    public static native float nativeGetFloorHeight();

    public static native int nativeGetSessionState();

    public static native String nativeGetSystemName();

    public static native boolean nativeIsEnabled();

    public static native int nativeGetEyeCount();

    public static native int nativeGetEyeRenderWidth();

    public static native int nativeGetEyeRenderHeight();

    public static native float nativeGetRefreshRate();

    public static native float[] nativeGetHeadPose();

    public static native float[] nativeGetEyeFov(int eye);

    public static native int[] nativeGetRecommendedResolution();

    public static native float[] nativeGetControllerPose(int hand);

    public static native float[] nativeGetControllerButtons(int hand);

    public static native void nativeVibrate(int hand, float amplitude, long durationNs,
        float frequency);

    public static native void nativeStopVibration(int hand);

    public static native float[] nativeGetPerformanceStats();

    public static void setEnabled(boolean enabled) {
        nativeSetEnabled(enabled);
    }

    public static void setRenderScale(float renderScale) {
        nativeSetRenderScale(Math.max(0.1f, Math.min(2.0f, renderScale)));
    }

    public static void setIPD(float ipd) {
        nativeSetIPD(Math.max(0.0f, ipd));
    }

    public static void setWorldScale(float worldScale) {
        nativeSetWorldScale(Math.max(0.01f, worldScale));
    }

    public static void setWorldOrientation(org.joml.Quaternionfc q) {
        nativeSetWorldOrientation(q.x(), q.y(), q.z(), q.w());
    }

    public static boolean isEnabled() {
        return nativeIsEnabled();
    }

    public static int getEyeCount() {
        return nativeGetEyeCount();
    }

    public static int getEyeRenderWidth() {
        return nativeGetEyeRenderWidth();
    }

    public static int getEyeRenderHeight() {
        return nativeGetEyeRenderHeight();
    }

    public static float getRefreshRate() {
        return nativeGetRefreshRate();
    }

    public static float[] getHeadPose() {
        return nativeGetHeadPose();
    }

    public static float[] getEyeFov(int eye) {
        return nativeGetEyeFov(eye);
    }

    public static int[] getRecommendedResolution() {
        return nativeGetRecommendedResolution();
    }

    public static float[] getControllerPose(int hand) {
        return nativeGetControllerPose(hand);
    }

    public static float[] getControllerButtons(int hand) {
        return nativeGetControllerButtons(hand);
    }

    public static void vibrate(int hand, float amplitude, long durationNs, float frequency) {
        nativeVibrate(hand, amplitude, durationNs, frequency);
    }

    public static void vibrate(int hand, float amplitude, int durationMs) {
        nativeVibrate(hand, amplitude, (long) durationMs * 1_000_000L, 0.0f);
    }

    public static void stopVibration(int hand) {
        nativeStopVibration(hand);
    }

    public static float[] getPerformanceStats() {
        return nativeGetPerformanceStats();
    }

    public static void setWorldPosition(float x, float y, float z) {
        nativeSetWorldPosition(x, y, z);
    }

    public static float[] getWorldPosition() {
        return nativeGetWorldPosition();
    }

    public static void recenter() {
        nativeRecenter();
    }

    public static float getFloorHeight() {
        return nativeGetFloorHeight();
    }

    public static int getSessionState() {
        return nativeGetSessionState();
    }

    public static boolean isSessionRunning() {
        int state = getSessionState();
        return state >= 2 && state <= 5;
    }

    public static boolean isDesktopPathActive() {
        return !isEnabled() || !isSessionRunning();
    }

    public static boolean startXRSession() {
        return nativeStartXRSession();
    }

    public static void stopXRSession() {
        nativeStopXRSession();
    }

    public static String getSystemName() {
        return nativeGetSystemName();
    }
}
