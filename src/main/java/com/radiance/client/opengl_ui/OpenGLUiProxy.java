package com.radiance.client.opengl_ui;

public final class OpenGLUiProxy {

    private OpenGLUiProxy() {
    }

    public static native long[] prepareFrame();

    public static native long[] exportSurface(int frameIndex);

    public static native void releaseWin32Handles(long memoryHandle,
        long vulkanReadySemaphoreHandle, long glCompleteSemaphoreHandle);

    public static native void submitFrame();

    public static native boolean hasResizePending();

    public static native boolean hasActiveFrame();

    public static native boolean prepareResize();

    public static native void completeResize();
}
