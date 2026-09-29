package com.radiance.client.texture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

public class AuxiliaryTextureReloader implements PreparableReloadListener {

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier synchronizer, ResourceManager manager,
        ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler, Executor prepareExecutor,
        Executor applyExecutor) {
        return AuxiliaryTextures.decodeImagesAsync(manager, prepareExecutor)
            .thenCompose(synchronizer::wait)
            .thenAcceptAsync(AuxiliaryTextures::applyDecodedImages, applyExecutor);
    }
}
