package com.radiance.client.mod_support.distant_horizons;

import com.radiance.client.vista.VistaTerrain;
import com.radiance.client.proxy.world.VistaProxy;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;

public class DistantHorizonsColumns {
    public static VistaTerrain copy(ColumnRenderSource source, IClientLevelWrapper level) {
        VistaTerrain.Builder builder = new VistaTerrain.Builder(level.getShade(EDhDirection.WEST),
            level.getShade(EDhDirection.EAST), level.getShade(EDhDirection.NORTH),
            level.getShade(EDhDirection.SOUTH));
        int width = 1 << source.getDataDetailLevel();
        int originX = DhSectionPos.getMinCornerBlockX(source.pos);
        int originZ = DhSectionPos.getMinCornerBlockZ(source.pos);
        try (ColumnRenderView column = ColumnRenderView.getPooled()) {
            for (int x = 0; x < ColumnRenderSource.WIDTH; x++) {
                for (int z = 0; z < ColumnRenderSource.WIDTH; z++) {
                    int blockX = originX + x * width;
                    int blockZ = originZ + z * width;
                    if (width < 16 && (blockX & 15) != 0 && ((blockX + width) & 15) != 0
                        && (blockZ & 15) != 0 && ((blockZ + width) & 15) != 0) {
                        continue;
                    }
                    source.populateColumnView(column, x, z);
                    for (int i = 0; i < column.size; i++) {
                        long data = column.get(i);
                        if (!RenderDataPointUtil.doesDataPointExist(data)
                            || RenderDataPointUtil.hasZeroHeight(data)) { break; }
                        byte material = RenderDataPointUtil.getBlockMaterialId(data);
                        if (material == EDhApiBlockMaterial.AIR.index) { continue; }
                        boolean water = material == EDhApiBlockMaterial.WATER.index;
                        int color = RenderDataPointUtil.getColor(data);
                        int rgba = (color & 0xff00ff00) | ((color >>> 16) & 255)
                            | ((color & 255) << 16);
                        float bias = water ? 1.0f / 9.0f + 0.001f : 0;
                        short texture = source.getTextureSetId(x, z, i);
                        builder.add(blockX, blockZ,
                            source.yOffset + RenderDataPointUtil.getYMin(data) - bias,
                            source.yOffset + RenderDataPointUtil.getYMax(data) - bias,
                            0, width, 0, width, rgba, water ? VistaProxy.MATERIAL_WATER : 0,
                            DistantHorizonsMaterials.face(material, texture, EDhDirection.WEST),
                            DistantHorizonsMaterials.face(material, texture, EDhDirection.EAST),
                            DistantHorizonsMaterials.face(material, texture, EDhDirection.NORTH),
                            DistantHorizonsMaterials.face(material, texture, EDhDirection.SOUTH));
                    }
                }
            }
        }
        return builder.build();
    }
}
