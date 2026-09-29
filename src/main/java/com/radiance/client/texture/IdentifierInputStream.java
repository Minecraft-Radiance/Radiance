package com.radiance.client.texture;

import java.io.IOException;
import java.io.InputStream;
import net.minecraft.resources.ResourceLocation;

public class IdentifierInputStream extends InputStream {

    private final ResourceLocation resourceId;
    private final InputStream originalStream;

    public IdentifierInputStream(InputStream originalStream, ResourceLocation id) {
        this.resourceId = id;
        this.originalStream = originalStream;
    }

    public ResourceLocation getResourceId() {
        return this.resourceId;
    }

    @Override
    public int read() throws IOException {
        return originalStream.read();
    }
}
