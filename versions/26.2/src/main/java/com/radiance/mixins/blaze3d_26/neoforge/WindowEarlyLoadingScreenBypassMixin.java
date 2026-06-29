package com.radiance.mixins.blaze3d_26.neoforge;

import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import com.radiance.client.blaze3d.backend.RadianceBackendWindowCreation;
import net.neoforged.fml.loading.EarlyLoadingScreenController;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;

@Mixin(Window.class)
public abstract class WindowEarlyLoadingScreenBypassMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean radiance$earlyWindowDisabled;

    @Redirect(
        method = "createGlfwWindow",
        at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/fml/loading/EarlyLoadingScreenController;current()Lnet/neoforged/fml/loading/EarlyLoadingScreenController;"
        )
    )
    private static EarlyLoadingScreenController radiance$skipEarlyWindowForRadiance() {
        EarlyLoadingScreenController controller = EarlyLoadingScreenController.current();
        if (radiance$earlyWindowDisabled) {
            return null;
        }
        if (!RadianceBackendWindowCreation.consumeRadianceWindowHintsApplied()) {
            return controller;
        }

        radiance$earlyWindowDisabled = true;
        radiance$closeEarlyWindow(controller);
        return null;
    }

    private static void radiance$closeEarlyWindow(EarlyLoadingScreenController controller) {
        if (controller == null) {
            return;
        }

        long window = 0L;
        try {
            window = controller.takeOverGlfwWindow();
            if (window != 0L && window != -1L) {
                GL.createCapabilities();
            }
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to take over NeoForge early loading window before creating Radiance window", throwable);
        }

        try {
            Method close = controller.getClass().getMethod("close");
            close.invoke(controller);
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Failed to close NeoForge early loading renderer before creating Radiance window", exception);
        }

        if (window != 0L && window != -1L) {
            GLFW.glfwMakeContextCurrent(0L);
            GL.setCapabilities(null);
            GLFW.glfwDestroyWindow(window);
        }
    }
}
