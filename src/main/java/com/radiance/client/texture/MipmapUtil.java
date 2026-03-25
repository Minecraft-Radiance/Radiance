package com.radiance.client.texture;

import net.minecraft.client.texture.NativeImage;

public final class MipmapUtil {

    private MipmapUtil() {
    }

    public static NativeImage getSpecificMipmapLevelImage(NativeImage source, int level) {
        return source;
    }
}
