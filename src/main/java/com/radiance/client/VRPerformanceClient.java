package com.radiance.client;

import com.radiance.client.keybindings.VRPerformanceKeys;
import com.radiance.client.gui.VRPerformanceManager;
import com.radiance.client.config.VRPerformanceConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client-side initialization for VR Performance Monitoring
 *
 * Sets up keybindings, event handlers, and initializes the VR performance
 * monitoring system for seamless integration with the Minecraft client.
 */
public class VRPerformanceClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Register keybindings
        for (var keyBinding : VRPerformanceKeys.getAllKeybindings()) {
            KeyBindingHelper.registerKeyBinding(keyBinding);
        }

        // Register client tick handler for key processing and updates
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Process VR performance keybindings
            VRPerformanceKeys.handleKeyPresses();

            // Update VR performance monitoring
            if (client.player != null) {
                VRPerformanceManager.getInstance().update();
            }
        });

        // Initialize configuration to defaults
        VRPerformanceConfig.resetToDefaults();

        // Log successful initialization
        System.out.println("[Radiance] VR Performance Monitoring initialized successfully");
    }
}