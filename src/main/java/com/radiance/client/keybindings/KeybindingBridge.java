package com.radiance.client.keybindings;

import com.radiance.client.gui.VRPerformanceManager;
import java.util.HashMap;
import java.util.Map;

//#if MC>=260200
//$$ import com.mojang.blaze3d.platform.InputConstants;
//$$ import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//$$ import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//$$ import net.minecraft.client.KeyMapping;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.resources.Identifier;
//#else
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
//#endif

public final class KeybindingBridge {
    private KeybindingBridge() {}

    public static final int KEY_F4 = 293;
    public static final int KEY_F5 = 294;
    public static final int KEY_F6 = 295;
    private static final Map<String, Object> CATEGORIES = new HashMap<>();

    //#if MC>=260200
    //$$ public static Object newKey(String translationKey, int keyCode, String categoryKey) {
    //$$     return new KeyMapping(translationKey, InputConstants.Type.KEYSYM, keyCode, category(categoryKey));
    //$$ }
    //$$
    //$$ private static KeyMapping.Category category(String categoryKey) {
    //$$     String prefix = "category.";
    //$$     String idText = categoryKey.startsWith(prefix) ? categoryKey.substring(prefix.length()) : categoryKey;
    //$$     int dot = idText.indexOf('.');
    //$$     Identifier id = dot < 0
    //$$         ? Identifier.withDefaultNamespace(idText)
    //$$         : Identifier.fromNamespaceAndPath(idText.substring(0, dot), idText.substring(dot + 1).replace('.', '/'));
    //$$     return (KeyMapping.Category) CATEGORIES.computeIfAbsent(categoryKey, ignored -> KeyMapping.Category.register(id));
    //$$ }
    //$$
    //$$ public static void register(Object key) {
    //$$     KeyMappingHelper.registerKeyMapping((KeyMapping) key);
    //$$ }
    //$$
    //$$ public static boolean wasPressed(Object key) {
    //$$     return ((KeyMapping) key).consumeClick();
    //$$ }
    //$$
    //$$ public static void registerClientTickHandlers() {
    //$$     ClientTickEvents.END_CLIENT_TICK.register(client -> {
    //$$         VRPerformanceKeys.handleKeyPresses();
    //$$         if (Minecraft.getInstance().player != null) {
    //$$             VRPerformanceManager.getInstance().update();
    //$$         }
    //$$     });
    //$$ }
    //#else
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
    //#endif

    public static void registerAll(Iterable<?> keys) {
        for (Object key : keys) {
            register(key);
        }
    }
}
