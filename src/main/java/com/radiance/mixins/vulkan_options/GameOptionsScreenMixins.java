package com.radiance.mixins.vulkan_options;

import net.minecraft.client.Options;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(OptionsSubScreen.class)
public abstract class GameOptionsScreenMixins {

    @Shadow
    protected OptionsList list;

    @Final
    @Shadow
    protected Options options;
}
