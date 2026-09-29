package com.radiance.client.vertex;

import com.radiance.client.render.BuiltBuffer;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.lwjgl.system.MemoryUtil;

public final class TransferredBuildBuffer implements AutoCloseable {

    private final BuiltBuffer builtBuffer;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private TransferredBuildBuffer(BuiltBuffer builtBuffer) {
        this.builtBuffer = Objects.requireNonNull(builtBuffer, "builtBuffer");
    }

    public static TransferredBuildBuffer take(BuiltBuffer builtBuffer) {
        return new TransferredBuildBuffer(builtBuffer);
    }

    public BuiltBuffer builtBuffer() {
        return builtBuffer;
    }

    public long vertexAddress() {
        return MemoryUtil.memAddress(builtBuffer.getBuffer());
    }

    public long sortedIndexAddress() {
        ByteBuffer sortedBuffer = builtBuffer.getSortedBuffer();
        return sortedBuffer == null ? 0L : MemoryUtil.memAddress(sortedBuffer);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        builtBuffer.close();
    }
}
