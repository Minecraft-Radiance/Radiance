package com.radiance.mixins.mod_support.distant_horizons;

import com.radiance.client.mod_support.distant_horizons.DistantHorizonsColumns;
import com.radiance.client.vista.VistaTerrain;
import com.radiance.client.mod_support.distant_horizons.IDistantHorizonsBufferContainerExt;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnRenderBufferBuilder;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection", remap = false)
public class LodRenderSectionMixins {
    @Unique
    private VistaTerrain radiance$terrain;

    @Redirect(method = "getAndBuildRenderData", at = @At(value = "INVOKE",
        target = "Lcom/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/ColumnRenderBufferBuilder;makeLodRenderData(Lcom/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/LodQuadBuilder;Lcom/seibel/distanthorizons/core/dataObjects/render/ColumnRenderSource;Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/IClientLevelWrapper;[Lcom/seibel/distanthorizons/core/dataObjects/render/ColumnRenderSource;[Z)V"), require = 1)
    private void copyTerrain(LodQuadBuilder builder, ColumnRenderSource source,
        IClientLevelWrapper level, ColumnRenderSource[] adjacent, boolean[] sameDetail) {
        ColumnRenderBufferBuilder.makeLodRenderData(builder, source, level, adjacent, sameDetail);
        radiance$terrain = DistantHorizonsColumns.copy(source, level);
    }

    @Redirect(method = "uploadToGpuAsync", at = @At(value = "INVOKE",
        target = "Lcom/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/LodBufferContainer;tryMakeAndUploadBuffersAsync(JLcom/seibel/distanthorizons/core/wrapperInterfaces/world/IClientLevelWrapper;Ljava/util/ArrayList;Ljava/util/ArrayList;)Ljava/util/concurrent/CompletableFuture;"), require = 1)
    private CompletableFuture<LodBufferContainer> attachTerrain(long pos, IClientLevelWrapper level,
        ArrayList<ByteBuffer> opaque, ArrayList<ByteBuffer> transparent) {
        VistaTerrain terrain = radiance$terrain;
        radiance$terrain = null;
        return LodBufferContainer.tryMakeAndUploadBuffersAsync(pos, level, opaque, transparent)
            .thenApply(container -> {
                ((IDistantHorizonsBufferContainerExt) container).radiance$setTerrain(terrain);
                return container;
            });
    }
}
