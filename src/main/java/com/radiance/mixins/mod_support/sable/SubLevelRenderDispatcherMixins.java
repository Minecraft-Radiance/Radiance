package com.radiance.mixins.mod_support.sable;

import com.radiance.client.mod_support.sable.SableBlockGeometry;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VanillaSubLevelRenderDispatcher.class, remap = false)
public class SubLevelRenderDispatcherMixins {
    @Inject(method = "createRenderData", at = @At("HEAD"), cancellable = true)
    private void radiance$createSource(ClientSubLevel subLevel, CallbackInfoReturnable<SubLevelRenderData> cir) {
        cir.setReturnValue(new SableBlockGeometry(subLevel));
    }

    @Inject(method = "resize", at = @At("HEAD"), cancellable = true)
    private void radiance$resizeSource(ClientSubLevel subLevel, SubLevelRenderData data,
        CallbackInfoReturnable<SubLevelRenderData> cir) {
        if (data instanceof SableBlockGeometry source) {
            source.resize();
            cir.setReturnValue(data);
        }
    }
}
