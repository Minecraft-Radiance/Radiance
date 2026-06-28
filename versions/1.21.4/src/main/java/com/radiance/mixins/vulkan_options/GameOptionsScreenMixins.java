package com.radiance.mixins.vulkan_options;

import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GameOptionsScreen.class)
public abstract class GameOptionsScreenMixins {

    @Shadow
    protected OptionListWidget body;

    @Final
    @Shadow
    protected GameOptions gameOptions;
}
