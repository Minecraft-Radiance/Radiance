package com.radiance.client.proxy.world;

import net.minecraft.world.phys.Vec3;

public class PlayerProxy {

    public static native void setCameraPos(double x, double y, double z);

    public static void setCameraPos(Vec3 cameraPos) {
        setCameraPos(cameraPos.x, cameraPos.y, cameraPos.z);
    }
}
