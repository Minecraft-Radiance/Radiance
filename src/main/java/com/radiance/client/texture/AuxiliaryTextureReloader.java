package com.radiance.client.texture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.util.profiler.Profiler;

public class AuxiliaryTextureReloader implements ResourceReloader {

    @Override
    public CompletableFuture<Void> reload(Synchronizer synchronizer, ResourceManager manager,
        Profiler prepareProfiler, Profiler applyProfiler, Executor prepareExecutor,
        Executor applyExecutor) {
        return AuxiliaryTextures.decodeImagesAsync(manager, prepareExecutor)
            .thenCompose(synchronizer::whenPrepared)
            .thenAcceptAsync(AuxiliaryTextures::applyDecodedImages, applyExecutor);
    }
}
