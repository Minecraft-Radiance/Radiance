package com.radiance.client.proxy.vulkan;

public final class VulkanUiProxy {

    private VulkanUiProxy() {
    }

    public static native void fuseWorld();

    public static native void postBlur();
}
