package com.radiance.client.proxy.vulkan;

/**
 * JNI proxy for VR system state managed on the C++ side.
 *
 * C++ owns all VR state (VRSystem). Java controls high-level settings
 * and reads back computed values (render resolution, eye count, etc.).
 *
 * In simulation mode, C++ populates eye parameters from window size and default FOV.
 * When OpenXR is integrated, C++ will populate from the HMD runtime instead,
 * and the Java API remains unchanged.
 */
public class VRProxy {

    // ---- Java → C++ (control) ----

    public static native void nativeSetEnabled(boolean enabled);

    /**
     * Create and start the OpenXR session (swapchains, input, etc.).
     * Call this when transitioning from flat/lobby mode to VR mode.
     * Returns true if session started successfully.
     */
    public static native boolean nativeStartXRSession();

    /**
     * Destroy the OpenXR session and return to flat mode.
     * Call this when leaving VR (e.g. returning to lobby / main menu).
     */
    public static native void nativeStopXRSession();

    public static native void nativeSetRenderScale(float renderScale);

    public static native void nativeSetIPD(float ipd);

    public static native void nativeSetWorldScale(float worldScale);

    /** Set VR tracking space orientation (quaternion: x, y, z, w). */
    public static native void nativeSetWorldOrientation(float qx, float qy, float qz, float qw);

    /** Set VR tracking space position offset (x, y, z). */
    public static native void nativeSetWorldPosition(float x, float y, float z);

    /** Get current world position offset [x, y, z]. */
    public static native float[] nativeGetWorldPosition();

    /** Recenter: snapshot current head position as the new tracking origin. */
    public static native void nativeRecenter();

    /** Get floor height in meters (head Y in STAGE reference space). */
    public static native float nativeGetFloorHeight();

    /**
     * Get OpenXR session state as an integer.
     * 0=UNKNOWN, 1=IDLE, 2=READY, 3=SYNCHRONIZED, 4=VISIBLE, 5=FOCUSED, 6=STOPPING, 7=LOSS_PENDING, 8=EXITING
     */
    public static native int nativeGetSessionState();

    /** Get the HMD system name (e.g. "Oculus Quest 3", "Valve Index"). */
    public static native String nativeGetSystemName();

    // ---- C++ → Java (query) ----

    public static native boolean nativeIsEnabled();

    public static native int nativeGetEyeCount();

    public static native int nativeGetEyeRenderWidth();

    public static native int nativeGetEyeRenderHeight();

    public static native float nativeGetRefreshRate();

    // ---- OpenXR head tracking / eye data ----

    /** Returns [px, py, pz, qx, qy, qz, qw] or zeroes if not valid. */
    public static native float[] nativeGetHeadPose();

    /** Returns [tanLeft, tanRight, tanUp, tanDown] for the given eye (0=left, 1=right). */
    public static native float[] nativeGetEyeFov(int eye);

    /** Returns [width, height] recommended per-eye resolution from the HMD runtime. */
    public static native int[] nativeGetRecommendedResolution();

    // ---- Controller input ----

    /**
     * Returns [px, py, pz, qx, qy, qz, qw, vx, vy, vz, valid] for the given hand.
     * hand: 0=left, 1=right. valid is 1.0 if tracking data is available.
     */
    public static native float[] nativeGetControllerPose(int hand);

    /**
     * Returns [triggerValue, gripValue, thumbstickX, thumbstickY,
     *          triggerPressed, gripPressed, primaryButton, secondaryButton,
     *          thumbstickClick, menuButton] for the given hand.
     * Analog values are 0.0-1.0; boolean values are 0.0 or 1.0.
     */
    public static native float[] nativeGetControllerButtons(int hand);

    // ---- Haptics ----

    /** Trigger haptic vibration on a hand (0=left, 1=right).
     *  amplitude: 0.0-1.0, durationNs: duration in nanoseconds, frequency: Hz (0 = runtime default). */
    public static native void nativeVibrate(int hand, float amplitude, long durationNs, float frequency);

    /** Stop haptic vibration on a hand (0=left, 1=right). */
    public static native void nativeStopVibration(int hand);

    // ---- Performance stats ----

    /**
     * Returns [gpuFrameTimeMs, cpuFrameTimeMs, cpuWorkMs, cpuWaitMs, compositorTargetMs, fps, droppedFrames, headroom].
     * cpuWorkMs = active CPU work; cpuWaitMs = time blocked in xrWaitFrame compositor pacing.
     * headroom = (target - max(cpuWorkMs, gpuMs)) / target; negative means frame drops.
     */
    public static native float[] nativeGetPerformanceStats();

    // ---- Convenience wrappers ----

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

    /**
     * Set the VR tracking space orientation from a full quaternion.
     * Encodes player body yaw, stick/mouse turning, elytra roll, etc.
     */
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

    // ---- Controller convenience wrappers ----

    /** Get controller pose for a hand (0=left, 1=right). */
    public static float[] getControllerPose(int hand) {
        return nativeGetControllerPose(hand);
    }

    /** Get controller buttons/axes for a hand (0=left, 1=right). */
    public static float[] getControllerButtons(int hand) {
        return nativeGetControllerButtons(hand);
    }

    /** Trigger haptic vibration. */
    public static void vibrate(int hand, float amplitude, long durationNs, float frequency) {
        nativeVibrate(hand, amplitude, durationNs, frequency);
    }

    /** Short convenience vibration (amplitude, duration in ms). */
    public static void vibrate(int hand, float amplitude, int durationMs) {
        nativeVibrate(hand, amplitude, (long) durationMs * 1_000_000L, 0.0f);
    }

    /** Stop vibration on a hand. */
    public static void stopVibration(int hand) {
        nativeStopVibration(hand);
    }

    /** Get performance stats [gpuMs, frameMs, targetMs, fps, drops, headroom]. */
    public static float[] getPerformanceStats() {
        return nativeGetPerformanceStats();
    }

    // ---- World position / recenter ----

    /** Set world position offset (tracking-space origin). */
    public static void setWorldPosition(float x, float y, float z) {
        nativeSetWorldPosition(x, y, z);
    }

    /** Get current world position offset [x, y, z]. */
    public static float[] getWorldPosition() {
        return nativeGetWorldPosition();
    }

    /** Recenter tracking space: snapshot current head position as origin. */
    public static void recenter() {
        nativeRecenter();
    }

    // ---- Session / device info ----

    /** Floor height in meters (head Y in STAGE reference space). */
    public static float getFloorHeight() {
        return nativeGetFloorHeight();
    }

    /** OpenXR session state (0=UNKNOWN .. 5=FOCUSED .. 8=EXITING). */
    public static int getSessionState() {
        return nativeGetSessionState();
    }

    /** True when OpenXR session is in an active renderable state. */
    public static boolean isSessionRunning() {
        int state = getSessionState();
        return state >= 2 && state <= 5; // READY..FOCUSED
    }

    /** True when renderer is using desktop/non-XR path for the current frame. */
    public static boolean isDesktopPathActive() {
        return !isEnabled() || !isSessionRunning();
    }

    /** Start the OpenXR session (transition from flat mode to VR). */
    public static boolean startXRSession() {
        return nativeStartXRSession();
    }

    /** Stop the OpenXR session (transition from VR back to flat mode). */
    public static void stopXRSession() {
        nativeStopXRSession();
    }

    /** HMD system name (e.g. "Oculus Quest 3"). */
    public static String getSystemName() {
        return nativeGetSystemName();
    }
}
