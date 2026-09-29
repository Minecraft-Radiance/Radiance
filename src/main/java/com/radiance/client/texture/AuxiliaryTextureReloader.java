package com.radiance.client.texture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;

public class AuxiliaryTextureReloader implements PreparableReloadListener {

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier synchronizer, ResourceManager resourceManager,
        Executor prepareExecutor, Executor applyExecutor) {
        return AuxiliaryTextures.decodeImagesAsync(resourceManager, prepareExecutor)
            .thenCompose(synchronizer::wait)
            .thenAcceptAsync(AuxiliaryTextures::applyDecodedImages, applyExecutor);
    }
}
