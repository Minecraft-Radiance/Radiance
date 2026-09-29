package com.radiance.mixins.vanilla_resource_tracker;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(FontManager.class)
public abstract class FontManagerMixins {
    @WrapMethod(method = "apply")
    private void synchronizeFontReload(@Coerce Object preparation, ProfilerFiller profiler,
        Operation<Void> original) {
        synchronized (this) {
            original.call(preparation, profiler);
        }
    }

    @WrapMethod(method = {
        "lambda$createFont$17(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/gui/font/FontSet;",
        "lambda$createFontFilterFishy$18(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/gui/font/FontSet;"
    })
    private FontSet synchronizeBackgroundFontLookup(ResourceLocation font,
        Operation<FontSet> original) {
        if (RenderSystem.isOnRenderThread()) {
            return original.call(font);
        }
        synchronized (this) {
            return original.call(font);
        }
    }
}
