package com.radiance.client.blaze3d.abi;

public final class ChunkProxy {

    private ChunkProxy() {
    }

    public static void rebuildAll() {
        com.radiance.client.proxy.world.ChunkProxy.rebuildAll();
    }
}
