package com.radiance.client.texture.compat;

import com.radiance.client.texture.AuxiliaryTextures;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
//#if MC>=260200
//$$ import net.minecraft.server.packs.resources.PreparableReloadListener;
//#else
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
//#endif

//#if MC>=260200
//$$ public abstract class AuxiliaryTextureReloaderCompat implements PreparableReloadListener {
//$$
//$$     @Override
//$$     public CompletableFuture<Void> reload(SharedState currentReload, Executor taskExecutor,
//$$         PreparationBarrier preparationBarrier, Executor reloadExecutor) {
//$$         return AuxiliaryTextures.prepareDecodedImagesAsync(currentReload.resourceManager(),
//$$                 taskExecutor)
//$$             .thenCompose(preparationBarrier::wait)
//$$             .thenAcceptAsync(AuxiliaryTextures::applyPreparedImages, reloadExecutor);
//$$     }
//$$ }
//#else
public abstract class AuxiliaryTextureReloaderCompat implements ResourceReloader {

    @Override
    public CompletableFuture<Void> reload(Synchronizer synchronizer, ResourceManager manager,
        Executor prepareExecutor, Executor applyExecutor) {
        return AuxiliaryTextures.prepareDecodedImagesAsync(manager, prepareExecutor)
            .thenCompose(synchronizer::whenPrepared)
            .thenAcceptAsync(AuxiliaryTextures::applyPreparedImages, applyExecutor);
    }
}
//#endif
