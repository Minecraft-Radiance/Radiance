package com.radiance.client.mod_support.distant_horizons;

import com.radiance.client.vista.VistaTerrain;
import com.radiance.client.proxy.world.VistaProxy;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IDhGenericObjectVertexBufferContainer;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeGenericObjectRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeGenericRenderCleanupEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeGenericRenderSetupEvent;
import com.seibel.distanthorizons.core.render.RenderParams;
import com.seibel.distanthorizons.core.render.renderer.RenderableBoxGroup;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.renderPass.IDhGenericRenderer;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.system.MemoryUtil;

public class DistantHorizonsObjects implements IDhGenericRenderer {
    private static final AtomicLong nextId = new AtomicLong(-1);
    private static final int[] normals = {0x80010000, 0x7fff0000, 0x7fff7fff, 0, 0x00008001, 0x00007fff};
    private static final int[][] faces = {
        {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1},
        {4, 5, 7, 6}, {0, 4, 6, 2}, {1, 3, 7, 5}
    };
    private final Map<Long, Group> groups = new ConcurrentHashMap<>();
    private long worldVersion;

    private static class Group {
        final RenderableBoxGroup source;
        List<BoxBuffer.Box> boxes;
        int light = -1;
        float[] shading;
        List<Mesh> active = new ArrayList<>();
        List<Mesh> pending = new ArrayList<>();
        Group(RenderableBoxGroup source) { this.source = source; }
    }

    private static class Mesh {
        final long id = nextId.getAndDecrement();
        final double x, y, z;
        ByteBuffer vertices;
        boolean submitted;
        Mesh(double x, double y, double z, ByteBuffer vertices) {
            this.x = x; this.y = y; this.z = z; this.vertices = vertices;
        }
        void close(long version) {
            if (vertices != null) { MemoryUtil.memFree(vertices); vertices = null; }
            if (submitted) { VistaProxy.invalidateSingle(version, id); }
        }
    }

    @Override
    public synchronized void add(IDhApiRenderableBoxGroup source) {
        if (!(source instanceof RenderableBoxGroup group)) {
            throw new IllegalArgumentException("Vista requires a Distant Horizons RenderableBoxGroup");
        }
        if (groups.putIfAbsent(group.getId(), new Group(group)) != null) {
            throw new IllegalArgumentException("Duplicate Vista group " + group.getId());
        }
        group.triggerBoxChange();
    }

    @Override
    public synchronized IDhApiRenderableBoxGroup remove(long id) {
        Group group = groups.remove(id);
        if (group == null) { return null; }
        clear(group);
        return group.source;
    }

    private void clear(Group group) {
        group.active.forEach(mesh -> mesh.close(worldVersion));
        group.pending.forEach(mesh -> mesh.close(worldVersion));
        group.active.clear();
        group.pending.clear();
        group.boxes = null;
    }

    public synchronized long[] prepare(RenderParams params, long version) {
        if (worldVersion != version) {
            groups.values().forEach(this::clear);
            worldVersion = version;
        }
        List<Long> selected = new ArrayList<>();
        ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeGenericRenderSetupEvent.class, params.apiCopy);
        for (Group group : groups.values()) {
            RenderableBoxGroup source = group.source;
            source.preRender(params.apiCopy);
            if (!source.isActive()) { continue; }
            DhApiBeforeGenericObjectRenderEvent.EventParam event = new DhApiBeforeGenericObjectRenderEvent.EventParam();
            event.update(params, source);
            if (ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeGenericObjectRenderEvent.class, event)) { continue; }
            source.tryUpdateInstancedDataAsync();
            BoxBuffer buffer = (BoxBuffer) source.vertexBufferContainer;
            List<BoxBuffer.Box> boxes = buffer.boxes();
            int light = source.getBlockLight() | (source.getSkyLight() << 4);
            var shade = source.getShading();
            float[] shading = shade == null ? new float[]{1, 1, 1, 1, 1, 1} :
                new float[]{shade.bottom, shade.top, shade.north, shade.south, shade.west, shade.east};
            if (group.boxes != boxes || group.light != light || !Arrays.equals(group.shading, shading)) {
                group.pending.forEach(mesh -> mesh.close(worldVersion));
                group.pending = build(boxes, light, shading, source.getResourceLocationNamespace().equals("DistantHorizons")
                    && source.getResourceLocationPath().equals("Beacons"));
                group.boxes = boxes;
                group.light = light;
                group.shading = shading;
            }
            boolean ready = true;
            for (Mesh mesh : group.pending) {
                if (!mesh.submitted) {
                    mesh.submitted = VistaProxy.rebuildSingle(worldVersion, mesh.id, 0, 0, 0, 0,
                        mesh.vertices, VistaTerrain.EMPTY);
                    if (mesh.submitted) { MemoryUtil.memFree(mesh.vertices); mesh.vertices = null; }
                }
                ready &= mesh.submitted && VistaProxy.isReady(worldVersion, mesh.id);
            }
            if (ready && (!group.pending.isEmpty() || boxes.isEmpty())) {
                group.active.forEach(mesh -> mesh.close(worldVersion));
                group.active = group.pending;
                group.pending = new ArrayList<>();
            }
            var origin = source.getOriginBlockPos();
            double x = source.positionBoxesRelativeToGroupOrigin ? origin.x : 0;
            double y = source.positionBoxesRelativeToGroupOrigin ? origin.y : 0;
            double z = source.positionBoxesRelativeToGroupOrigin ? origin.z : 0;
            for (Mesh mesh : group.active) {
                VistaProxy.moveObject(worldVersion, mesh.id, mesh.x + x, mesh.y + y, mesh.z + z);
                selected.add(mesh.id);
            }
            source.postRender(params.apiCopy);
        }
        ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeGenericRenderCleanupEvent.class, params.apiCopy);
        return selected.stream().mapToLong(Long::longValue).toArray();
    }

    private static List<Mesh> build(List<BoxBuffer.Box> boxes, int light, float[] shading, boolean beacon) {
        List<Mesh> meshes = new ArrayList<>();
        for (int start = 0; start < boxes.size();) {
            int end = start + 1;
            BoxBuffer.Box first = boxes.get(start);
            double x = Math.floor(first.minX()), y = Math.floor(first.minY()), z = Math.floor(first.minZ());
            while (end < Math.min(start + 1024, boxes.size())) {
                BoxBuffer.Box next = boxes.get(end);
                if (Math.abs(next.minX() - x) > 4096 || Math.abs(next.minY() - y) > 4096
                    || Math.abs(next.minZ() - z) > 4096) { break; }
                end++;
            }
            ByteBuffer vertices = MemoryUtil.memAlloc((end - start) * 24 * 40).order(ByteOrder.nativeOrder());
            for (int index = start; index < end; index++) {
                BoxBuffer.Box box = boxes.get(index);
                int color = box.color();
                int material = beacon ? 15 : box.material();
                int alpha = material == 12 || (color >>> 24) < 255 ? 2 : 0;
                int packed = 1 | 16 | 32 | (alpha << 8) | DistantHorizonsMaterials.emission(material);
                for (int face = 0; face < 6; face++) {
                    float shade = Float.isFinite(shading[face]) ? Math.max(0, shading[face]) : 1;
                    int rgba = (color & 0xff000000)
                        | Math.min(255, Math.round(((color >>> 16) & 255) * shade))
                        | (Math.min(255, Math.round(((color >>> 8) & 255) * shade)) << 8)
                        | (Math.min(255, Math.round((color & 255) * shade)) << 16);
                    for (int corner : faces[face]) {
                        vertices.putFloat((float) (((corner & 1) == 0 ? box.minX() : box.maxX()) - x));
                        vertices.putFloat((float) (((corner & 2) == 0 ? box.minY() : box.maxY()) - y));
                        vertices.putFloat((float) (((corner & 4) == 0 ? box.minZ() : box.maxZ()) - z));
                        vertices.putInt(normals[face]).putFloat(0).putFloat(0).putInt(rgba);
                        vertices.putInt(((light >>> 4) * 16 << 16) | ((light & 15) * 16));
                        vertices.putInt(DistantHorizonsMaterials.encode(material, 0)).putInt(packed);
                    }
                }
            }
            vertices.flip();
            meshes.add(new Mesh(x, y, z, vertices));
            start = end;
        }
        return meshes;
    }

    @Override
    public void render(RenderParams params, IProfilerWrapper profiler, boolean ssao) {
        VistaProxy.updateObjectListNative(worldVersion, prepare(params, worldVersion));
    }

    public synchronized void reset() { groups.values().forEach(this::clear); }

    @Override public String getVboRenderDebugMenuString() { return "Vista objects: " + groups.size(); }
    @Override public synchronized void close() { reset(); groups.clear(); }

    static class BoxBuffer implements IDhGenericObjectVertexBufferContainer {
        public record Box(double minX, double minY, double minZ, double maxX, double maxY,
            double maxZ, int color, int material) {}

        private volatile EState state = EState.NEW;
        private volatile List<Box> boxes = List.of();
        private volatile boolean closed;

        public List<Box> boxes() { return boxes; }

        @Override
        public synchronized void updateVertexData(List<DhApiRenderableBox> source) {
            List<Box> copy = new ArrayList<>();
            synchronized (source) {
                for (DhApiRenderableBox box : source) {
                    if (box == null || box.color == null || box.minPos == null || box.maxPos == null) {
                        throw new IllegalArgumentException("Distant Horizons box data must not be null");
                    }
                    if (!Double.isFinite(box.minPos.x) || !Double.isFinite(box.minPos.y)
                        || !Double.isFinite(box.minPos.z) || !Double.isFinite(box.maxPos.x)
                        || !Double.isFinite(box.maxPos.y) || !Double.isFinite(box.maxPos.z)) {
                        throw new IllegalArgumentException("Distant Horizons box positions must be finite");
                    }
                    if (box.minPos.x >= box.maxPos.x || box.minPos.y >= box.maxPos.y
                        || box.minPos.z >= box.maxPos.z || box.color.getAlpha() == 0) { continue; }
                    copy.add(new Box(box.minPos.x, box.minPos.y, box.minPos.z,
                        box.maxPos.x, box.maxPos.y, box.maxPos.z, box.color.getRGB(), box.material));
                }
            }
            if (!closed) { boxes = List.copyOf(copy); }
        }

        @Override public synchronized void uploadDataToGpu() { if (!closed) { state = EState.RENDER; } }
        @Override public EState getState() { return state; }
        @Override public synchronized void setState(EState value) { if (!closed) { state = value; } }
        @Override public synchronized void close() { closed = true; boxes = List.of(); state = EState.NEW; }
    }
}
