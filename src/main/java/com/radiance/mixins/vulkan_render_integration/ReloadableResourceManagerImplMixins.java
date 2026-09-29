package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.shader.ShaderRegistry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableResourceManager.class)
public class ReloadableResourceManagerImplMixins {

    @Inject(method = "registerReloadListener(Lnet/minecraft/server/packs/resources/PreparableReloadListener;)V", at = @At(value = "HEAD"))
    public void addInfo(PreparableReloadListener reloader, CallbackInfo ci) {






    }

    @Inject(method = "createReload(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)Lnet/minecraft/server/packs/resources/ReloadInstance;", at = @At("HEAD"))
    private void clearShaderCache(Executor prepareExecutor, Executor applyExecutor,
        CompletableFuture<Unit> initialStage, List<PackResources> packs,
        CallbackInfoReturnable<ReloadInstance> cir) {
        ShaderRegistry.clear();
    }
}
