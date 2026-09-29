package com.radiance.client.proxy.world;

import com.radiance.client.profiler.Profiler;
import java.util.BitSet;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;

public final class BlockEntitySectionCache {
    private static final ChunkBuilder.BuiltChunk[] EMPTY =
        new ChunkBuilder.BuiltChunk[0];
    private static final BitSet nonempty = new BitSet();
    private static ChunkBuilder.BuiltChunk[] source;
    private static ChunkBuilder.BuiltChunk[] snapshot;
    private static boolean indexed;

    private BlockEntitySectionCache() {}

    public static synchronized void clear() {
        source = null;
        snapshot = null;
        nonempty.clear();
        indexed = false;
    }

    public static synchronized void changed(ChunkBuilder.BuiltChunk section) {
        int index = section.index;
        if (source == null || !indexed || index < 0 || index >= source.length
            || source[index] != section) return;
        boolean wasNonempty = nonempty.get(index);
        boolean isNonempty = !section.getData().getBlockEntities().isEmpty();
        nonempty.set(index, isNonempty);
        if (wasNonempty || isNonempty) snapshot = null;
    }

    public static synchronized ChunkBuilder.BuiltChunk[] sections(BuiltChunkStorage area) {
        boolean rebuilt = false;
        if (source != area.chunks) {
            source = area.chunks;
            nonempty.clear();
            snapshot = null;
            indexed = true;
            for (int i = 0; i < source.length; i++) {
                var section = source[i];
                if (section == null || section.index != i) {
                    indexed = false;
                    break;
                }
                if (!section.getData().getBlockEntities().isEmpty()) nonempty.set(i);
            }
            rebuilt = true;
        }
        if (!indexed) {
            if (Profiler.ENABLED) {
                Profiler.metric("block_entities.sections_total", source.length);
                Profiler.metric("block_entities.sections_scanned", source.length);
                Profiler.metric("block_entities.section_cache_fallback", 1);
            }
            return source;
        }
        if (snapshot == null) {
            int count = nonempty.cardinality();
            snapshot = count == 0 ? EMPTY : new ChunkBuilder.BuiltChunk[count];
            int next = 0;
            for (int i = nonempty.nextSetBit(0); i >= 0; i = nonempty.nextSetBit(i + 1)) {
                snapshot[next++] = source[i];
            }
            rebuilt = true;
        }
        if (Profiler.ENABLED) {
            Profiler.metric("block_entities.sections_total", source.length);
            Profiler.metric("block_entities.sections_scanned", snapshot.length);
            Profiler.metric("block_entities.section_cache_rebuilt", rebuilt ? 1 : 0);
            Profiler.metric("block_entities.section_cache_reused", rebuilt ? 0 : 1);
        }
        return snapshot;
    }
}
