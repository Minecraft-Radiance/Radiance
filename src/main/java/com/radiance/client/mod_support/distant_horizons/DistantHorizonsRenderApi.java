package com.radiance.client.mod_support.distant_horizons;

import com.seibel.distanthorizons.api.enums.config.EDhApiRenderingApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiRenderingEngine;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.EDhDepthRange;
import com.seibel.distanthorizons.core.render.EDhRenderDepth;
import com.seibel.distanthorizons.core.render.renderer.AbstractDebugWireframeRenderer;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.AbstractDhRenderApiDefinition;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.renderPass.*;

public class DistantHorizonsRenderApi extends AbstractDhRenderApiDefinition {
    public static DistantHorizonsDebug debug;

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Distant Horizons raster passes are replaced by MCVR main rendering; use the Vista scene adapter");
    }

    @Override public String getEngineName() { return "Radiance Vista"; }
    @Override public boolean useSingleIbo() { return true; }
    @Override public EDhRenderDepth getRenderDepth() { return EDhRenderDepth.FORWARD_Z; }
    @Override public EDhDepthRange getDepthRange() { return EDhDepthRange.ZERO_TO_POS_ONE; }
    @Override public EDhApiRenderingApi getRenderApi() { return EDhApiRenderingApi.VULKAN; }
    @Override public EDhApiRenderingEngine getRenderingEngine() { return EDhApiRenderingEngine.AUTO; }
    @Override public boolean isNativeRenderer() { return true; }

    @Override
    public void bindRenderers() {
        SingletonInjector.INSTANCE.bind(AbstractDhRenderApiDefinition.class, this);
        debug = new DistantHorizonsDebug();
        SingletonInjector.INSTANCE.bind(AbstractDebugWireframeRenderer.class, debug);
    }

    @Override public IDhMetaRenderer getMetaRenderer() { throw unsupported(); }
    @Override public IDhTerrainRenderer getTerrainRenderer() { throw unsupported(); }
    @Override public IDhSsaoRenderer getSsaoRenderer() { throw unsupported(); }
    @Override public IDhFogRenderer getFogRenderer() { throw unsupported(); }
    @Override public IDhFarFadeRenderer getFarFadeRenderer() { throw unsupported(); }
    @Override public IDhAntiAliasRenderer getAntiAliasRenderer() { throw unsupported(); }
    @Override public AbstractDebugWireframeRenderer getDebugWireframeRenderer() { return debug; }
    @Override public IDhVanillaFadeRenderer getVanillaFadeRenderer() { throw unsupported(); }
    @Override public IDhTestTriangleRenderer getTestTriangleRenderer() { throw unsupported(); }

    @Override public IVertexBufferWrapper createVboWrapper(String name) { return new DistantHorizonsVertexBuffer(); }
    @Override public ILodContainerUniformBufferWrapper createLodContainerUniformWrapper() {
        return new ILodContainerUniformBufferWrapper() {
            @Override public void tryUpload(LodBufferContainer container) { }
            @Override public void close() { }
        };
    }
    @Override public IDhGenericObjectVertexBufferContainer createGenericVboContainer() {
        return new DistantHorizonsObjects.BoxBuffer();
    }
    @Override public IDhGenericRenderer createGenericRenderer() { return new DistantHorizonsObjects(); }
}
