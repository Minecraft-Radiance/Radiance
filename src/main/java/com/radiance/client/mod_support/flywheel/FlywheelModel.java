package com.radiance.client.mod_support.flywheel;

import com.radiance.client.proxy.world.EntityProxy;
import dev.engine_room.flywheel.api.material.*;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.*;
import dev.engine_room.flywheel.lib.vertex.FullVertexView;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.system.MemoryUtil;

final class FlywheelModel implements AutoCloseable {
    record Mesh(int handle, Material material) {}
    final List<Mesh> meshes = new ArrayList<>();

    FlywheelModel(Model model) {
        try {
            for (var configured : model.meshes()) {
                var mesh = configured.mesh();
                if (mesh.vertexCount() == 0 || mesh.indexCount() == 0) continue;
                Material material = configured.material();
                if (!material.shaders().fragmentSource().equals(StandardMaterialShaders.DEFAULT.fragmentSource())
                    || !material.fog().equals(FogShaders.LINEAR)
                    || (!material.light().equals(LightShaders.SMOOTH_WHEN_EMBEDDED) && !material.light().equals(LightShaders.SMOOTH)
                        && !material.light().equals(LightShaders.FLAT))
                    || (material.transparency() != Transparency.OPAQUE && material.transparency() != Transparency.TRANSLUCENT
                        && material.transparency() != Transparency.ORDER_INDEPENDENT)
                    || material.depthTest() != DepthTest.LEQUAL || material.writeMask() != WriteMask.COLOR_DEPTH
                    || material.polygonOffset() || material.blur())
                    throw new UnsupportedOperationException("Flywheel fragment material cannot be represented by Entity PBR: fragment="
                        + material.shaders().fragmentSource() + ", fog=" + material.fog() + ", light=" + material.light()
                        + ", transparency=" + material.transparency() + ", depth=" + material.depthTest()
                        + ", writeMask=" + material.writeMask() + ", polygonOffset=" + material.polygonOffset() + ", blur=" + material.blur());
                int alpha;
                if (material.cutout().equals(CutoutShaders.OFF)) alpha = 0;
                else if (material.cutout().equals(CutoutShaders.HALF)) alpha = 1;
                else if (material.cutout().equals(CutoutShaders.ONE_TENTH)) alpha = 5;
                else if (material.cutout().equals(CutoutShaders.EPSILON)) alpha = 6;
                else throw new UnsupportedOperationException("Unknown Flywheel cutout shader: " + material.cutout());
                if (material.transparency() != Transparency.OPAQUE) {
                    alpha = switch (alpha) { case 0 -> 2; case 1 -> 7; case 5 -> 8; case 6 -> 9; default -> throw new AssertionError(); };
                }
                int flags = alpha << 16;
                if (material.useOverlay()) flags |= 4;
                if (material.useLight()) flags |= 8;
                int bytes = Math.addExact(20, Math.addExact(Math.multiplyExact(mesh.vertexCount(), 36), Math.multiplyExact(mesh.indexCount(), 4)));
                ByteBuffer buffer = MemoryUtil.memAlloc(bytes);
                try {
                    buffer.putInt(1).putInt(mesh.vertexCount()).putInt(mesh.indexCount())
                        .putInt(MinecraftClient.getInstance().getTextureManager().getTexture(material.texture()).getGlId()).putInt(flags);
                    FullVertexView vertices = new FullVertexView();
                    vertices.ptr(MemoryUtil.memAddress(buffer)); vertices.vertexCount(mesh.vertexCount());
                    mesh.write(vertices); buffer.position(buffer.position() + mesh.vertexCount() * 36);
                    mesh.indexSequence().fill(MemoryUtil.memAddress(buffer), mesh.indexCount());
                    buffer.position(buffer.position() + mesh.indexCount() * 4); buffer.flip();
                    meshes.add(new Mesh(EntityProxy.registerPrebuiltGeometryNative(buffer), material));
                } finally { MemoryUtil.memFree(buffer); }
            }
        } catch (RuntimeException error) { close(); throw error; }
    }
    @Override
    public void close() {
        for (Mesh mesh : meshes) EntityProxy.releasePrebuiltGeometryNative(mesh.handle());
        meshes.clear();
    }
}
