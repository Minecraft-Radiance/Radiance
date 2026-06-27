package com.radiance.client.texture;

import java.io.IOException;
import java.io.InputStream;
//#if MC>=260200
//$$ import net.minecraft.resources.Identifier;
//#else
import net.minecraft.util.Identifier;
//#endif

public class IdentifierInputStream extends InputStream {

    private final Identifier resourceId;
    private final InputStream originalStream;

    public IdentifierInputStream(InputStream originalStream, Identifier id) {
        this.resourceId = id;
        this.originalStream = originalStream;
    }

    public Identifier getResourceId() {
        return this.resourceId;
    }

    @Override
    public int read() throws IOException {
        return originalStream.read();
    }
}
