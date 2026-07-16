package com.radiance.client.keybindings;

import com.radiance.client.gui.VRPerformanceManager;
import com.radiance.client.option.Options;
import java.util.List;

/**
 * Keybinding registry for VR performance monitoring controls
 *
 * Provides convenient keyboard shortcuts for toggling VR performance display
 * and adjusting visualization settings in real-time.
 */
public class VRPerformanceKeys {

    // Keybindings
    public static final Object TOGGLE_VR_PERFORMANCE = KeybindingBridge.newKey(
        "key.radiance.toggle_vr_performance",
        KeybindingBridge.KEY_F4,
        "category.radiance.vr"
    );

    public static final Object TOGGLE_VR_CHARTS = KeybindingBridge.newKey(
        "key.radiance.toggle_vr_charts",
        KeybindingBridge.KEY_F5,
        "category.radiance.vr"
    );

    public static final Object CYCLE_VR_POSITION = KeybindingBridge.newKey(
        "key.radiance.cycle_vr_position",
        KeybindingBridge.KEY_F6,
        "category.radiance.vr"
    );

    /**
     * Process key presses for VR performance controls
     * Call this from the client tick handler
     */
    public static void handleKeyPresses() {
        // Toggle VR performance display in F3
        if (KeybindingBridge.wasPressed(TOGGLE_VR_PERFORMANCE)) {
            VRPerformanceManager.getInstance().toggleF3Display();

            // Could add chat message or sound feedback here
            // MinecraftClient client = MinecraftClient.getInstance();
            // if (client.player != null) {
            //     boolean enabled = VRPerformanceManager.getInstance().isF3DisplayEnabled();
            //     client.player.sendMessage(Text.literal("VR Performance Display: " + (enabled ? "ON" : "OFF")), true);
            // }
        }

        // Toggle detailed charts
        if (KeybindingBridge.wasPressed(TOGGLE_VR_CHARTS)) {
            Options.setVRF3Charts(!Options.vrF3Charts, true);
        }

        // Cycle HUD position
        if (KeybindingBridge.wasPressed(CYCLE_VR_POSITION)) {
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
    public static List<Object> getAllKeybindings() {
        return List.of(
            TOGGLE_VR_PERFORMANCE,
            TOGGLE_VR_CHARTS,
            CYCLE_VR_POSITION
        );
    }
}
