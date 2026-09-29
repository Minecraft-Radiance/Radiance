package com.radiance.client.mod_support.distant_horizons;

import com.seibel.distanthorizons.api.enums.config.EDhApiDepthDirection;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthRange;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;

public final class DistantHorizonsRenderApi332 extends DistantHorizonsRenderApi {
    @Override public EDhApiDepthDirection getDepthDirection() { return EDhApiDepthDirection.FORWARD_Z; }
    @Override public EDhApiDepthRange getDepthRange() { return EDhApiDepthRange.ZERO_TO_POS_ONE; }

    @Override
    protected <T> void setApiValue(ConfigEntry<T> config, T value) {
        config.setApiValue(value, getEngineName());
    }
}
