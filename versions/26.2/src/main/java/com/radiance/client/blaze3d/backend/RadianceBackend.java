package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.GLFWErrorCapture;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;
import com.radiance.client.blaze3d.abi.RendererProxy;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class RadianceBackend implements GpuBackend {
    @Override
    public String getName() {
        return "Radiance Blaze3D";
    }

    @Override
    public void setWindowHints() {
        GLFW.glfwWindowHint(139265, 0);
    }

    @Override
    public void handleWindowCreationErrors(GLFWErrorCapture.@Nullable Error error) throws BackendCreationException {
        if (error != null) {
            throw new BackendCreationException("GLFW_ERROR: 0x" + Integer.toHexString(error.error()), BackendCreationException.Reason.GLFW_ERROR);
        }
    }

    @Override
    public GpuDevice createDevice(long window, ShaderSource defaultShaderSource, GpuDebugOptions debugOptions,
                                  Runnable criticalShaderLoader) throws BackendCreationException {
        try {
            Blaze3DProxy.initRendererNative(RendererProxy.glfwLibraryCandidates(), window);
            return new GpuDevice(new RadianceGpuDeviceBackend(), criticalShaderLoader);
        } catch (Throwable throwable) {
            throw new BackendCreationException("Failed to initialize Radiance Blaze3D backend: " + throwable.getMessage(),
                BackendCreationException.Reason.OTHER);
        }
    }
}
