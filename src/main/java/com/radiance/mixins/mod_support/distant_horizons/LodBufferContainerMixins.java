package com.radiance.mixins.mod_support.distant_horizons;

import com.radiance.client.vista.VistaTerrain;
import com.radiance.client.mod_support.distant_horizons.IDistantHorizonsBufferContainerExt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer", remap = false)
public class LodBufferContainerMixins implements IDistantHorizonsBufferContainerExt {
    @Unique
    private VistaTerrain radiance$terrain;

    @Override
    public VistaTerrain radiance$getTerrain() { return radiance$terrain; }

    @Override
    public void radiance$setTerrain(VistaTerrain terrain) { radiance$terrain = terrain; }
}
