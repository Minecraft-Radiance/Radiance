package com.radiance.client.keybindings;

import com.radiance.client.gui.VRPerformanceManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class KeybindingBridge {
    private KeybindingBridge() {
    }

    public static final int KEY_F4 = 293;
    public static final int KEY_F5 = 294;
    public static final int KEY_F6 = 295;

    public static Object newKey(String translationKey, int keyCode, String categoryKey) {
        return new KeyBinding(translationKey, InputUtil.Type.KEYSYM, keyCode, categoryKey);
    }

    public static void register(Object key) {
        KeyBindingHelper.registerKeyBinding((KeyBinding) key);
    }

    public static boolean wasPressed(Object key) {
        return ((KeyBinding) key).wasPressed();
    }

    public static void registerClientTickHandlers() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            VRPerformanceKeys.handleKeyPresses();
            if (client.player != null) {
                VRPerformanceManager.getInstance().update();
            }
        });
    }

    public static void registerAll(Iterable<?> keys) {
        for (Object key : keys) {
            register(key);
        }
    }
}
