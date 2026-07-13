package com.radiance.client.scene_import.sodium;

//#if MC>=260200
//$$ import com.radiance.client.RadianceClient;
//$$ import com.radiance.client.blaze3d.backend.RadianceTextureHandles;
//$$ import com.radiance.client.constant.Constants;
//$$ import com.radiance.client.proxy.world.ChunkProxy;
//$$ import java.nio.ByteBuffer;
//$$ import java.nio.ByteOrder;
//$$ import java.util.ArrayList;
//$$ import java.util.List;
//$$ import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.multiplayer.ClientLevel;
//$$ import net.minecraft.client.renderer.texture.TextureAtlas;
//$$ import net.minecraft.core.SectionPos;
//$$ import net.minecraft.util.Mth;
//$$ import org.joml.Vector3dc;
//$$ import org.lwjgl.system.MemoryUtil;
//$$
//$$ public final class SodiumChunkMeshBridge {
//$$     private static final int PBR_VERTEX_FORMAT_ID = 12;
//$$     private static volatile int blockAtlasTextureId;
//$$     private static volatile int lastCameraSectionX = Integer.MIN_VALUE;
//$$     private static volatile int lastCameraSectionY = Integer.MIN_VALUE;
//$$     private static volatile int lastCameraSectionZ = Integer.MIN_VALUE;
//$$
//$$     private SodiumChunkMeshBridge() {
//$$     }
//$$
//$$     public static void initGrid(ClientLevel level, int renderDistance) {
//$$         if (level == null) {
//$$             return;
//$$         }
//$$         ChunkProxy.initSodium(renderDistance, level.getMinSectionY(), level.getMaxSectionY());
//$$         lastCameraSectionX = Integer.MIN_VALUE;
//$$         lastCameraSectionY = Integer.MIN_VALUE;
//$$         lastCameraSectionZ = Integer.MIN_VALUE;
//$$         blockAtlasTextureId = 0;
//$$     }
//$$
//$$     public static void updateCameraSection(Vector3dc cameraPosition) {
//$$         if (cameraPosition == null) {
//$$             return;
//$$         }
//$$         int sectionX = SectionPos.blockToSectionCoord(Mth.floor(cameraPosition.x()));
//$$         int sectionY = SectionPos.blockToSectionCoord(Mth.floor(cameraPosition.y()));
//$$         int sectionZ = SectionPos.blockToSectionCoord(Mth.floor(cameraPosition.z()));
//$$         if (sectionX == lastCameraSectionX && sectionY == lastCameraSectionY
//$$             && sectionZ == lastCameraSectionZ) {
//$$             return;
//$$         }
//$$         lastCameraSectionX = sectionX;
//$$         lastCameraSectionY = sectionY;
//$$         lastCameraSectionZ = sectionZ;
//$$         ChunkProxy.updateSodiumCameraSection(sectionX, sectionY, sectionZ);
//$$     }
//$$
//$$     public static void invalidateSection(int sectionX, int sectionY, int sectionZ) {
//$$         ChunkProxy.invalidateSodiumSection(sectionX, sectionY, sectionZ);
//$$     }
//$$
//$$     public static void prepareBuildOutput(ChunkBuildOutput output) {
//$$         if (output == null) {
//$$             return;
//$$         }
//$$         SodiumChunkBuildOutputAccess access = (SodiumChunkBuildOutputAccess) output;
//$$         SodiumPreparedSection previous = access.radiance$takePreparedSection();
//$$         if (previous != null) {
//$$             previous.close();
//$$         }
//$$         try {
//$$             access.radiance$setPreparedSection(SodiumCompactMeshDecoder.decode(output));
//$$         } catch (RuntimeException exception) {
//$$             RadianceClient.LOGGER.error("Failed to prepare Sodium section mesh at ({}, {}, {})",
//$$                 output.section.getChunkX(), output.section.getChunkY(), output.section.getChunkZ(),
//$$                 exception);
//$$         }
//$$     }
//$$
//$$     public static void submitAcceptedBuildOutput(ChunkBuildOutput output) {
//$$         if (output == null) {
//$$             return;
//$$         }
//$$         SodiumPreparedSection section =
//$$             ((SodiumChunkBuildOutputAccess) output).radiance$takePreparedSection();
//$$         if (section == null) {
//$$             return;
//$$         }
//$$         try (section) {
//$$             submit(section);
//$$         }
//$$     }
//$$
//$$     public static void discardBuildOutput(ChunkBuildOutput output) {
//$$         if (output == null) {
//$$             return;
//$$         }
//$$         SodiumPreparedSection section =
//$$             ((SodiumChunkBuildOutputAccess) output).radiance$takePreparedSection();
//$$         if (section != null) {
//$$             section.close();
//$$         }
//$$     }
//$$
//$$     private static void submit(SodiumPreparedSection section) {
//$$         List<SodiumPreparedSection.Mesh> meshes = section.meshes();
//$$         if (meshes.isEmpty()) {
//$$             ChunkProxy.invalidateSodiumSection(section.sectionX(), section.sectionY(),
//$$                 section.sectionZ());
//$$             return;
//$$         }
//$$
//$$         ByteBuffer geometryTypes = null;
//$$         ByteBuffer geometryGroupNames = null;
//$$         ByteBuffer geometryTextures = null;
//$$         ByteBuffer rasterMetadata = null;
//$$         ByteBuffer sceneTags = null;
//$$         ByteBuffer vertexFormats = null;
//$$         ByteBuffer vertexCounts = null;
//$$         ByteBuffer vertices = null;
//$$         List<ByteBuffer> nativeNames = new ArrayList<>(meshes.size());
//$$         try {
//$$             int count = meshes.size();
//$$             geometryTypes = memAlloc(count * Integer.BYTES);
//$$             geometryGroupNames = memAlloc(count * Long.BYTES);
//$$             geometryTextures = memAlloc(count * Integer.BYTES);
//$$             rasterMetadata = memAlloc(count * Constants.RasterMetadata.INT_STRIDE * Integer.BYTES);
//$$             sceneTags = memAlloc(count * Constants.SceneTags.INT_STRIDE * Integer.BYTES);
//$$             vertexFormats = memAlloc(count * Integer.BYTES);
//$$             vertexCounts = memAlloc(count * Integer.BYTES);
//$$             vertices = memAlloc(count * Long.BYTES);
//$$             int[] metadata = new int[Constants.RasterMetadata.INT_STRIDE];
//$$             int[] tags = new int[Constants.SceneTags.INT_STRIDE];
//$$             int atlasTextureId = blockAtlasTextureId();
//$$
//$$             for (int i = 0; i < count; i++) {
//$$                 SodiumPreparedSection.Mesh mesh = meshes.get(i);
//$$                 SodiumPreparedSection.Pass pass = mesh.pass();
//$$                 ByteBuffer nativeName = MemoryUtil.memUTF8(pass.groupName(), true);
//$$                 nativeNames.add(nativeName);
//$$                 geometryTypes.putInt(i * Integer.BYTES,
//$$                     Constants.GeometryTypes.getGeometryType(pass.layerName(), true).getValue());
//$$                 geometryGroupNames.putLong(i * Long.BYTES, MemoryUtil.memAddress(nativeName));
//$$                 geometryTextures.putInt(i * Integer.BYTES, atlasTextureId);
//$$                 Constants.RasterMetadata.write(metadata, 0, pass.layerName(),
//$$                     pass.translucent() ? Constants.RasterMetadata.BLEND_ALPHA
//$$                         : Constants.RasterMetadata.BLEND_OPAQUE,
//$$                     !pass.translucent(), true, true);
//$$                 for (int m = 0; m < metadata.length; m++) {
//$$                     rasterMetadata.putInt((i * metadata.length + m) * Integer.BYTES, metadata[m]);
//$$                 }
//$$                 Constants.SceneTags.writeVanillaChunk(tags, 0);
//$$                 for (int t = 0; t < tags.length; t++) {
//$$                     sceneTags.putInt((i * tags.length + t) * Integer.BYTES, tags[t]);
//$$                 }
//$$                 vertexFormats.putInt(i * Integer.BYTES, PBR_VERTEX_FORMAT_ID);
//$$                 vertexCounts.putInt(i * Integer.BYTES, mesh.vertexCount());
//$$                 vertices.putLong(i * Long.BYTES, MemoryUtil.memAddress(mesh.vertices()));
//$$             }
//$$
//$$             ChunkProxy.submitSodiumSection(section.originX(), section.originY(), section.originZ(),
//$$                 section.sectionX(), section.sectionY(), section.sectionZ(), count,
//$$                 MemoryUtil.memAddress(geometryTypes), MemoryUtil.memAddress(geometryGroupNames),
//$$                 MemoryUtil.memAddress(geometryTextures), MemoryUtil.memAddress(rasterMetadata),
//$$                 MemoryUtil.memAddress(sceneTags), MemoryUtil.memAddress(vertexFormats),
//$$                 MemoryUtil.memAddress(vertexCounts), MemoryUtil.memAddress(vertices),
//$$                 section.important());
//$$         } finally {
//$$             free(geometryTypes);
//$$             free(geometryGroupNames);
//$$             free(geometryTextures);
//$$             free(rasterMetadata);
//$$             free(sceneTags);
//$$             free(vertexFormats);
//$$             free(vertexCounts);
//$$             free(vertices);
//$$             for (ByteBuffer nativeName : nativeNames) {
//$$                 free(nativeName);
//$$             }
//$$         }
//$$     }
//$$
//$$     private static int blockAtlasTextureId() {
//$$         int cached = blockAtlasTextureId;
//$$         if (cached != 0) {
//$$             return cached;
//$$         }
//$$         try {
//$$             var texture = Minecraft.getInstance().getTextureManager()
//$$                 .getTexture(TextureAtlas.LOCATION_BLOCKS);
//$$             cached = RadianceTextureHandles.textureId(texture.getTextureView().texture());
//$$         } catch (RuntimeException ignored) {
//$$             cached = 0;
//$$         }
//$$         blockAtlasTextureId = cached;
//$$         return cached;
//$$     }
//$$
//$$     private static ByteBuffer memAlloc(int size) {
//$$         return MemoryUtil.memAlloc(size).order(ByteOrder.nativeOrder());
//$$     }
//$$
//$$     private static void free(ByteBuffer buffer) {
//$$         if (buffer != null) {
//$$             MemoryUtil.memFree(buffer);
//$$         }
//$$     }
//$$ }
//#else
public final class SodiumChunkMeshBridge {
    private SodiumChunkMeshBridge() {
    }
}
//#endif
