package com.radiance.client.keybindings;

import com.radiance.client.gui.VRPerformanceManager;
import com.radiance.client.option.Options;
import java.util.List;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Keybinding registry for VR performance monitoring controls
 *
 * Provides convenient keyboard shortcuts for toggling VR performance display
 * and adjusting visualization settings in real-time.
 */
public class VRPerformanceKeys {

    // Keybindings
    public static final KeyBinding TOGGLE_VR_PERFORMANCE = new KeyBinding(
        "key.radiance.toggle_vr_performance",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_F4, // F4 to toggle VR performance overlay
        "category.radiance.vr"
    );

    public static final KeyBinding TOGGLE_VR_CHARTS = new KeyBinding(
        "key.radiance.toggle_vr_charts",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_F5, // F5 to toggle detailed charts
        "category.radiance.vr"
    );

    public static final KeyBinding CYCLE_VR_POSITION = new KeyBinding(
        "key.radiance.cycle_vr_position",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_F6, // F6 to cycle HUD position
        "category.radiance.vr"
    );

    /**
     * Process key presses for VR performance controls
     * Call this from the client tick handler
     */
    public static void handleKeyPresses() {
        // Toggle VR performance display in F3
        if (TOGGLE_VR_PERFORMANCE.wasPressed()) {
            VRPerformanceManager.getInstance().toggleF3Display();

            // Could add chat message or sound feedback here
            // MinecraftClient client = MinecraftClient.getInstance();
            // if (client.player != null) {
            //     boolean enabled = VRPerformanceManager.getInstance().isF3DisplayEnabled();
            //     client.player.sendMessage(Text.literal("VR Performance Display: " + (enabled ? "ON" : "OFF")), true);
            // }
        }

        // Toggle detailed charts
        if (TOGGLE_VR_CHARTS.wasPressed()) {
            Options.setVRF3Charts(!Options.vrF3Charts, true);
        }

        // Cycle HUD position
        if (CYCLE_VR_POSITION.wasPressed()) {
            cycleHudPosition();
        }
    }

    /**
     * Cycle through HUD positions
     */
    private static void cycleHudPosition() {
        switch (com.radiance.client.config.VRPerformanceConfig.hudPosition) {
            case TOP_LEFT:
                Options.setVRF3ChartPosition(
                    com.radiance.client.config.VRPerformanceConfig.HudPosition.TOP_RIGHT, true);
                break;
            case TOP_RIGHT:
                Options.setVRF3ChartPosition(
                    com.radiance.client.config.VRPerformanceConfig.HudPosition.BOTTOM_RIGHT, true);
                break;
            case BOTTOM_RIGHT:
                Options.setVRF3ChartPosition(
                    com.radiance.client.config.VRPerformanceConfig.HudPosition.BOTTOM_LEFT, true);
                break;
            case BOTTOM_LEFT:
                Options.setVRF3ChartPosition(
                    com.radiance.client.config.VRPerformanceConfig.HudPosition.TOP_LEFT, true);
                break;
            default:
                Options.setVRF3ChartPosition(
                    com.radiance.client.config.VRPerformanceConfig.HudPosition.TOP_RIGHT, true);
                break;
        }
    }

    /**
     * Get all keybindings for registration with Minecraft
     */
    public static List<KeyBinding> getAllKeybindings() {
        return List.of(
            TOGGLE_VR_PERFORMANCE,
            TOGGLE_VR_CHARTS,
            CYCLE_VR_POSITION
        );
    }
}
