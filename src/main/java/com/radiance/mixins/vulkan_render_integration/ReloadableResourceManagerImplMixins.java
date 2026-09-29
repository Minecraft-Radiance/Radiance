package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.shader.ShaderRegistry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReloadableResourceManagerImpl.class)
public class ReloadableResourceManagerImplMixins {

    @Inject(method = "registerReloader(Lnet/minecraft/resource/ResourceReloader;)V", at = @At(value = "HEAD"))
    public void addInfo(ResourceReloader reloader, CallbackInfo ci) {






    }

    @Inject(method = "reload", at = @At("HEAD"))
    private void clearShaderCache(Executor prepareExecutor, Executor applyExecutor,
        CompletableFuture<Unit> initialStage, List<ResourcePack> packs,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<ResourceReload> cir) {
        ShaderRegistry.clear();
    }
}
