package com.radiance.client.mod_support.distant_horizons;

import com.seibel.distanthorizons.core.render.EDhRenderDepth;
import com.seibel.distanthorizons.core.render.EDhDepthRange;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;

public final class DistantHorizonsRenderApi331 extends DistantHorizonsRenderApi {
    @Override public EDhRenderDepth getRenderDepth() { return EDhRenderDepth.FORWARD_Z; }
    @Override public EDhDepthRange getDepthRange() { return EDhDepthRange.ZERO_TO_POS_ONE; }

    @Override
    protected <T> void setApiValue(ConfigEntry<T> config, T value) {
        config.setApiValue(value);
    }
}
