package com.radiance.client.scene_import.sodium;

//#if MC>=260200
//$$ import java.nio.ByteBuffer;
//$$ import java.nio.ByteOrder;
//$$ import java.util.ArrayList;
//$$ import java.util.List;
//$$ import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
//$$ import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
//$$ import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
//$$ import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
//$$ import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
//$$ import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
//$$ import org.joml.Vector3fc;
//$$ import org.lwjgl.system.MemoryUtil;
//$$
//$$ public final class SodiumCompactMeshDecoder {
//$$     private static final int COMPACT_STRIDE = 20;
//$$     private static final int PBR_STRIDE = 128;
//$$     private static final float POSITION_SCALE = 32.0F / (1 << 20);
//$$     private static final float POSITION_OFFSET = -8.0F;
//$$     private static final float TEXTURE_SCALE = 1.0F / (1 << 15);
//$$     private static final int ALPHA_MODE_OPAQUE = 0;
//$$     private static final int ALPHA_MODE_CUTOUT = 1;
//$$     private static final int ALPHA_MODE_TRANSPARENT = 2;
//$$
//$$     private SodiumCompactMeshDecoder() {
//$$     }
//$$
//$$     public static SodiumPreparedSection decode(ChunkBuildOutput output) {
//$$         RenderSection section = output.section;
//$$         List<SodiumPreparedSection.Mesh> meshes = new ArrayList<>(output.meshes.size());
//$$         try {
//$$             addMesh(meshes, output, DefaultTerrainRenderPasses.SOLID,
//$$                 SodiumPreparedSection.Pass.SOLID);
//$$             addMesh(meshes, output, DefaultTerrainRenderPasses.CUTOUT,
//$$                 SodiumPreparedSection.Pass.CUTOUT);
//$$             addMesh(meshes, output, DefaultTerrainRenderPasses.TRANSLUCENT,
//$$                 SodiumPreparedSection.Pass.TRANSLUCENT);
//$$             return new SodiumPreparedSection(section.getOriginX(), section.getOriginY(),
//$$                 section.getOriginZ(), section.getChunkX(), section.getChunkY(),
//$$                 section.getChunkZ(), output.blockingTask, meshes);
//$$         } catch (Throwable throwable) {
//$$             for (SodiumPreparedSection.Mesh mesh : meshes) {
//$$                 MemoryUtil.memFree(mesh.vertices());
//$$             }
//$$             throw throwable;
//$$         }
//$$     }
//$$
//$$     private static void addMesh(List<SodiumPreparedSection.Mesh> target,
//$$         ChunkBuildOutput output, TerrainRenderPass sodiumPass,
//$$         SodiumPreparedSection.Pass pass) {
//$$         BuiltSectionMeshParts parts = output.getMesh(sodiumPass);
//$$         if (parts == null) {
//$$             return;
//$$         }
//$$         ByteBuffer source = parts.getVertexData().getDirectBuffer().order(ByteOrder.nativeOrder());
//$$         if (source.remaining() == 0) {
//$$             return;
//$$         }
//$$         if (source.remaining() % COMPACT_STRIDE != 0) {
//$$             throw new IllegalArgumentException("Invalid Sodium compact mesh byte length: "
//$$                 + source.remaining());
//$$         }
//$$         int vertexCount = source.remaining() / COMPACT_STRIDE;
//$$         ByteBuffer vertices = MemoryUtil.memAlloc(vertexCount * PBR_STRIDE)
//$$             .order(ByteOrder.nativeOrder());
//$$         MemoryUtil.memSet(MemoryUtil.memAddress(vertices), 0, vertices.capacity());
//$$         try {
//$$             decodeSegments(source, parts.getVertexSegments(), pass, vertices, vertexCount);
//$$             target.add(new SodiumPreparedSection.Mesh(pass, vertexCount, vertices));
//$$         } catch (Throwable throwable) {
//$$             MemoryUtil.memFree(vertices);
//$$             throw throwable;
//$$         }
//$$     }
//$$
//$$     private static void decodeSegments(ByteBuffer source, int[] segments,
//$$         SodiumPreparedSection.Pass pass, ByteBuffer target, int vertexCount) {
//$$         int vertexOffset = 0;
//$$         float[] positions = new float[12];
//$$         float[] normal = new float[3];
//$$         for (int segmentIndex = 0; segmentIndex + 1 < segments.length; segmentIndex += 2) {
//$$             int segmentVertexCount = segments[segmentIndex];
//$$             if (segmentVertexCount == 0) {
//$$                 continue;
//$$             }
//$$             if (segmentVertexCount < 0 || (segmentVertexCount & 3) != 0
//$$                 || vertexOffset + segmentVertexCount > vertexCount) {
//$$                 throw new IllegalArgumentException("Invalid Sodium vertex segment");
//$$             }
//$$             int facingIndex = segments[segmentIndex + 1];
//$$             if (facingIndex < 0 || facingIndex >= ModelQuadFacing.COUNT) {
//$$                 throw new IllegalArgumentException("Invalid Sodium facing index: " + facingIndex);
//$$             }
//$$             ModelQuadFacing facing = ModelQuadFacing.VALUES[facingIndex];
//$$             for (int quadOffset = 0; quadOffset < segmentVertexCount; quadOffset += 4) {
//$$                 decodeQuad(source, vertexOffset + quadOffset, facing, pass, target,
//$$                     positions, normal);
//$$             }
//$$             vertexOffset += segmentVertexCount;
//$$         }
//$$         if (vertexOffset != vertexCount) {
//$$             throw new IllegalArgumentException("Sodium segments describe " + vertexOffset
//$$                 + " vertices, but the mesh contains " + vertexCount);
//$$         }
//$$     }
//$$
//$$     private static void decodeQuad(ByteBuffer source, int firstVertex,
//$$         ModelQuadFacing facing, SodiumPreparedSection.Pass pass, ByteBuffer target,
//$$         float[] positions, float[] normal) {
//$$         for (int vertex = 0; vertex < 4; vertex++) {
//$$             decodePosition(source, (firstVertex + vertex) * COMPACT_STRIDE,
//$$                 positions, vertex * 3);
//$$         }
//$$         if (facing.isAligned()) {
//$$             alignedNormal(facing, normal);
//$$         } else {
//$$             normal(positions, normal);
//$$         }
//$$         for (int vertex = 0; vertex < 4; vertex++) {
//$$             writeVertex(source, firstVertex + vertex, positions, vertex * 3, normal, pass,
//$$                 target);
//$$         }
//$$     }
//$$
//$$     private static void decodePosition(ByteBuffer source, int base, float[] positions,
//$$         int positionOffset) {
//$$         int high = source.getInt(base);
//$$         int low = source.getInt(base + 4);
//$$         positions[positionOffset] = decodePositionComponent(high, low, 0);
//$$         positions[positionOffset + 1] = decodePositionComponent(high, low, 10);
//$$         positions[positionOffset + 2] = decodePositionComponent(high, low, 20);
//$$     }
//$$
//$$     private static float decodePositionComponent(int high, int low, int shift) {
//$$         int value = ((high >>> shift) & 0x3FF) << 10 | ((low >>> shift) & 0x3FF);
//$$         return value * POSITION_SCALE + POSITION_OFFSET;
//$$     }
//$$
//$$     private static void alignedNormal(ModelQuadFacing facing, float[] target) {
//$$         Vector3fc normal = facing.getAlignedNormal();
//$$         target[0] = normal.x();
//$$         target[1] = normal.y();
//$$         target[2] = normal.z();
//$$     }
//$$
//$$     private static void normal(float[] p, float[] target) {
//$$         float ax = p[3] - p[0];
//$$         float ay = p[4] - p[1];
//$$         float az = p[5] - p[2];
//$$         float bx = p[6] - p[0];
//$$         float by = p[7] - p[1];
//$$         float bz = p[8] - p[2];
//$$         float nx = ay * bz - az * by;
//$$         float ny = az * bx - ax * bz;
//$$         float nz = ax * by - ay * bx;
//$$         float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
//$$         if (length <= 1.0E-6F) {
//$$             target[0] = 0.0F;
//$$             target[1] = 1.0F;
//$$             target[2] = 0.0F;
//$$             return;
//$$         }
//$$         target[0] = nx / length;
//$$         target[1] = ny / length;
//$$         target[2] = nz / length;
//$$     }
//$$
//$$     private static void writeVertex(ByteBuffer source, int vertexIndex, float[] positions,
//$$         int positionOffset, float[] normal, SodiumPreparedSection.Pass pass,
//$$         ByteBuffer target) {
//$$         int sourceBase = vertexIndex * COMPACT_STRIDE;
//$$         int targetBase = vertexIndex * PBR_STRIDE;
//$$         int color = source.getInt(sourceBase + 8);
//$$         int texture = source.getInt(sourceBase + 12);
//$$         int lightAndData = source.getInt(sourceBase + 16);
//$$
//$$         target.putFloat(targetBase, positions[positionOffset]);
//$$         target.putFloat(targetBase + 4, positions[positionOffset + 1]);
//$$         target.putFloat(targetBase + 8, positions[positionOffset + 2]);
//$$         target.putInt(targetBase + 12, 1);
//$$         target.putFloat(targetBase + 16, normal[0]);
//$$         target.putFloat(targetBase + 20, normal[1]);
//$$         target.putFloat(targetBase + 24, normal[2]);
//$$         target.putInt(targetBase + 28, 1);
//$$         target.putFloat(targetBase + 32, (color & 0xFF) / 255.0F);
//$$         target.putFloat(targetBase + 36, (color >>> 8 & 0xFF) / 255.0F);
//$$         target.putFloat(targetBase + 40, (color >>> 16 & 0xFF) / 255.0F);
//$$         target.putFloat(targetBase + 44, (color >>> 24 & 0xFF) / 255.0F);
//$$         target.putInt(targetBase + 48, 1);
//$$         target.putFloat(targetBase + 56, (texture & 0x7FFF) * TEXTURE_SCALE);
//$$         target.putFloat(targetBase + 60, (texture >>> 16 & 0x7FFF) * TEXTURE_SCALE);
//$$         target.putInt(targetBase + 92, 1);
//$$         target.putInt(targetBase + 96, Math.max(0, (lightAndData & 0xFF) - 8));
//$$         target.putInt(targetBase + 100, Math.max(0, (lightAndData >>> 8 & 0xFF) - 8));
//$$         target.putInt(targetBase + 124, alphaMode(pass, lightAndData >>> 16 & 0xFF));
//$$     }
//$$
//$$     private static int alphaMode(SodiumPreparedSection.Pass pass, int materialBits) {
//$$         if (pass.translucent()) {
//$$             return ALPHA_MODE_TRANSPARENT;
//$$         }
//$$         int alphaCutoff = materialBits >>> 1 & 0x3;
//$$         return alphaCutoff == 0 ? ALPHA_MODE_OPAQUE : ALPHA_MODE_CUTOUT;
//$$     }
//$$ }
//#else
public final class SodiumCompactMeshDecoder {
    private SodiumCompactMeshDecoder() {
    }
}
//#endif
