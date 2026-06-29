package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.GLFWErrorCapture;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;
import com.radiance.client.blaze3d.abi.RendererProxy;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.Presets;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class RadianceBackend implements GpuBackend {
    @Override
    public String getName() {
        return "Radiance Blaze3D";
    }

    @Override
    public void setWindowHints() {
        RadianceBackendWindowCreation.markRadianceWindowHintsApplied();
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
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
            int clientApi = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_CLIENT_API);
            if (clientApi != GLFW.GLFW_NO_API) {
                throw new BackendCreationException(
                    "Radiance Blaze3D requires a GLFW_NO_API window, got GLFW_CLIENT_API=0x"
                        + Integer.toHexString(clientApi),
                    BackendCreationException.Reason.GLFW_ERROR);
            }
            Blaze3DProxy.initRendererNative(RendererProxy.glfwLibraryCandidates(), window);
            Pipeline.collectNativeModules();
            Pipeline.switchToPresetMode(Presets.DEFERRED_RT_NRD_FSR.key, false);
            Pipeline.build();
            return new GpuDevice(new RadianceGpuDeviceBackend(), criticalShaderLoader);
        } catch (BackendCreationException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new BackendCreationException("Failed to initialize Radiance Blaze3D backend: " + throwable.getMessage(),
                BackendCreationException.Reason.OTHER);
        }
    }
}
