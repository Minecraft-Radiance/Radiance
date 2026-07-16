package com.radiance.client.blaze3d.abi;

import java.util.concurrent.atomic.AtomicInteger;

public final class ShaderProxy {

    private static final AtomicInteger NEXT_SHADER_ID = new AtomicInteger(1);

    private ShaderProxy() {
    }

    public static int registerShader(String shaderKey, int vertexFormatType, int drawMode,
        int uniformSize, String vertexShaderPath, String fragmentShaderPath, String[] defineNames,
        String[] defineValues) {
        return NEXT_SHADER_ID.getAndIncrement();
    }
}
