package com.radiance.mixins.vanilla_resource_tracker;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.FontManager;
import net.minecraft.client.font.FontStorage;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(FontManager.class)
public abstract class FontManagerMixins {
    @Shadow
    private volatile FontStorage currentStorage;

    @WrapMethod(method = "reload(Lnet/minecraft/client/font/FontManager$ProviderIndex;Lnet/minecraft/util/profiler/Profiler;)V")
    private void synchronizeFontReload(@Coerce Object preparation, Profiler profiler,
        Operation<Void> original) {
        synchronized (this) {
            try {
                original.call(preparation, profiler);
            } finally {
                currentStorage = null;
            }
        }
    }

    @WrapMethod(method = "getStorage")
    private FontStorage synchronizeBackgroundFontLookup(Identifier font,
        Operation<FontStorage> original) {
        if (RenderSystem.isOnRenderThread()) {
            return original.call(font);
        }
        synchronized (this) {
            return original.call(font);
        }
    }
}
