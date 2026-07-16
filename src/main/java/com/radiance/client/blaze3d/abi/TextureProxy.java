package com.radiance.client.blaze3d.abi;

import java.util.concurrent.atomic.AtomicInteger;

public final class TextureProxy {

    private static final AtomicInteger NEXT_TEXTURE_ID = new AtomicInteger(1);

    private TextureProxy() {
    }

    public static int generateTextureId() {
        return NEXT_TEXTURE_ID.getAndIncrement();
    }

    public static void uploadEmissionTile(Object tileUpdate) {
    }

    public static void flushEmissionTiles() {
    }

    public static boolean hasEmissionTile(int textureId, long tileKey) {
        return false;
    }
}
