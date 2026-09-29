package com.radiance.client.render;

import java.nio.ByteBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import org.jetbrains.annotations.Nullable;

public class BuiltBuffer implements AutoCloseable {

    private final ByteBuffer buffer;
    @Nullable
    private final ByteBuffer sortedBuffer;
    private final DrawParameters drawParameters;
    private final Runnable closer;
    private boolean closed = false;

    public BuiltBuffer(ByteBuffer buffer, @Nullable ByteBuffer sortedBuffer,
        DrawParameters drawParameters, Runnable closer) {
        this.buffer = buffer;
        this.sortedBuffer = sortedBuffer;
        this.drawParameters = drawParameters;
        this.closer = closer;
    }

    public static BuiltBuffer wrap(BufferBuilder.BuiltBuffer builtBuffer) {
        BufferBuilder.DrawParameters parameters = builtBuffer.getParameters();
        ByteBuffer sortedBuffer = parameters.sequentialIndex() ? null : builtBuffer.getIndexBuffer();
        return new BuiltBuffer(builtBuffer.getVertexBuffer(), sortedBuffer,
            new DrawParameters(parameters.format(), parameters.vertexCount(),
                parameters.indexCount(), parameters.mode(), parameters.indexType()),
            builtBuffer::release);
    }

    public ByteBuffer getBuffer() {
        return this.buffer;
    }

    @Nullable
    public ByteBuffer getSortedBuffer() {
        return this.sortedBuffer;
    }

    public DrawParameters getDrawParameters() {
        return this.drawParameters;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            this.closer.run();
        }
    }

    public record DrawParameters(VertexFormat format, int vertexCount, int indexCount,
                                 VertexFormat.DrawMode mode, VertexFormat.IndexType indexType) {
    }
}
