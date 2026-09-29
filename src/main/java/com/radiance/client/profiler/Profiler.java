package com.radiance.client.profiler;

public final class Profiler {

    public static final boolean ENABLED = ProfilerBuildConfig.ENABLED;

    private Profiler() {
    }

    private static native boolean nativeIsAvailable();

    private static native void nativeEnter(String name);

    private static native void nativeExit();

    private static native void nativeMetric(String name, long value);

    private static native void nativeFlush();

    public static void initialize() {
        if (!nativeIsAvailable()) {
            throw new IllegalStateException("Native profiler is unavailable");
        }
    }

    public static void enter(String name) {
        nativeEnter(name);
    }

    public static void exit() {
        nativeExit();
    }

    public static void metric(String name, long value) {
        nativeMetric(name, value);
    }

    public static void flush() {
        nativeFlush();
    }
}
