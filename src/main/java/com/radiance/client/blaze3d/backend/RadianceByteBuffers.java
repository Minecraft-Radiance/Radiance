package com.radiance.client.blaze3d.backend;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class RadianceByteBuffers {
    private RadianceByteBuffers() {
    }

    static ByteBuffer allocateDirectNative(long size) {
        return ByteBuffer.allocateDirect(Math.toIntExact(size)).order(ByteOrder.nativeOrder());
    }
}
