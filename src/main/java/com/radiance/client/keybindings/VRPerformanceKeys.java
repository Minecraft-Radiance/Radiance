package com.radiance.client.keybindings;

import com.radiance.client.gui.VRPerformanceManager;
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
            com.radiance.client.config.VRPerformanceConfig.showDetailedCharts =
                !com.radiance.client.config.VRPerformanceConfig.showDetailedCharts;
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
        var config = com.radiance.client.config.VRPerformanceConfig.class;

        switch (com.radiance.client.config.VRPerformanceConfig.hudPosition) {
            case TOP_LEFT:
                com.radiance.client.config.VRPerformanceConfig.hudPosition =
                    com.radiance.client.config.VRPerformanceConfig.HudPosition.TOP_RIGHT;
                break;
            case TOP_RIGHT:
                com.radiance.client.config.VRPerformanceConfig.hudPosition =
                    com.radiance.client.config.VRPerformanceConfig.HudPosition.BOTTOM_RIGHT;
                break;
            case BOTTOM_RIGHT:
                com.radiance.client.config.VRPerformanceConfig.hudPosition =
                    com.radiance.client.config.VRPerformanceConfig.HudPosition.BOTTOM_LEFT;
                break;
            case BOTTOM_LEFT:
                com.radiance.client.config.VRPerformanceConfig.hudPosition =
                    com.radiance.client.config.VRPerformanceConfig.HudPosition.TOP_LEFT;
                break;
            default:
                com.radiance.client.config.VRPerformanceConfig.hudPosition =
                    com.radiance.client.config.VRPerformanceConfig.HudPosition.TOP_RIGHT;
                break;
        }
    }

    /**
     * Get all keybindings for registration with Minecraft
     */
    public static KeyBinding[] getAllKeybindings() {
        return new KeyBinding[] {
            TOGGLE_VR_PERFORMANCE,
            TOGGLE_VR_CHARTS,
            CYCLE_VR_POSITION
        };
    }
}