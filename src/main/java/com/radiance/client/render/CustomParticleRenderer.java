package com.radiance.client.render;

import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;

public final class CustomParticleRenderer {

    private static final ThreadLocal<CaptureState> ACTIVE = new ThreadLocal<>();

    private CustomParticleRenderer() {
    }

    public static void begin(StorageVertexConsumerProvider storageVertexConsumerProvider,
        RenderLayer fallbackLayer) {
        ACTIVE.set(new CaptureState(storageVertexConsumerProvider, fallbackLayer,
            new ArrayList<>()));
    }

    public static boolean capture(RenderLayer renderLayer, BuiltBuffer builtBuffer) {
        CaptureState captureState = ACTIVE.get();
        if (captureState == null) {
            return false;
        }

        captureState.capturedLayers()
            .add(new EntityProxy.EntityRenderLayer(renderLayer, builtBuffer, true));
        return true;
    }

    public static boolean isCapturing() {
        return ACTIVE.get() != null;
    }

    public static VertexConsumerProvider getVertexConsumerProvider(VertexConsumerProvider fallback) {
        CaptureState captureState = ACTIVE.get();
        return captureState == null ? fallback : captureState.storageVertexConsumerProvider();
    }

    public static VertexConsumer getFallbackVertexConsumer() {
        CaptureState captureState = ACTIVE.get();
        if (captureState == null) {
            throw new IllegalStateException("Custom particle capture is not active");
        }

        return captureState.storageVertexConsumerProvider()
            .getBuffer(captureState.fallbackLayer());
    }

    public static List<EntityProxy.EntityRenderLayer> end() {
        CaptureState captureState = ACTIVE.get();
        ACTIVE.remove();
        if (captureState == null) {
            return List.of();
        }
        return captureState.capturedLayers();
    }

    private record CaptureState(StorageVertexConsumerProvider storageVertexConsumerProvider,
                                RenderLayer fallbackLayer,
                                List<EntityProxy.EntityRenderLayer> capturedLayers) {
    }
}
