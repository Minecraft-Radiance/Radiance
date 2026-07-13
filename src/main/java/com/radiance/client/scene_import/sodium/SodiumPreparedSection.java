package com.radiance.client.scene_import.sodium;

//#if MC>=260200
//$$ import java.nio.ByteBuffer;
//$$ import java.util.List;
//$$ import org.lwjgl.system.MemoryUtil;
//$$
//$$ public final class SodiumPreparedSection implements AutoCloseable {
//$$     private final int originX;
//$$     private final int originY;
//$$     private final int originZ;
//$$     private final int sectionX;
//$$     private final int sectionY;
//$$     private final int sectionZ;
//$$     private final boolean important;
//$$     private final List<Mesh> meshes;
//$$     private boolean closed;
//$$
//$$     public SodiumPreparedSection(int originX, int originY, int originZ, int sectionX,
//$$         int sectionY, int sectionZ, boolean important, List<Mesh> meshes) {
//$$         this.originX = originX;
//$$         this.originY = originY;
//$$         this.originZ = originZ;
//$$         this.sectionX = sectionX;
//$$         this.sectionY = sectionY;
//$$         this.sectionZ = sectionZ;
//$$         this.important = important;
//$$         this.meshes = List.copyOf(meshes);
//$$     }
//$$
//$$     public int originX() {
//$$         return this.originX;
//$$     }
//$$
//$$     public int originY() {
//$$         return this.originY;
//$$     }
//$$
//$$     public int originZ() {
//$$         return this.originZ;
//$$     }
//$$
//$$     public int sectionX() {
//$$         return this.sectionX;
//$$     }
//$$
//$$     public int sectionY() {
//$$         return this.sectionY;
//$$     }
//$$
//$$     public int sectionZ() {
//$$         return this.sectionZ;
//$$     }
//$$
//$$     public boolean important() {
//$$         return this.important;
//$$     }
//$$
//$$     public List<Mesh> meshes() {
//$$         if (this.closed) {
//$$             throw new IllegalStateException("Sodium section payload is already closed");
//$$         }
//$$         return this.meshes;
//$$     }
//$$
//$$     @Override
//$$     public void close() {
//$$         if (this.closed) {
//$$             return;
//$$         }
//$$         this.closed = true;
//$$         for (Mesh mesh : this.meshes) {
//$$             MemoryUtil.memFree(mesh.vertices());
//$$         }
//$$     }
//$$
//$$     public record Mesh(Pass pass, int vertexCount, ByteBuffer vertices) {
//$$     }
//$$
//$$     public enum Pass {
//$$         SOLID("sodium:solid", "solid", false),
//$$         CUTOUT("sodium:cutout", "cutout", false),
//$$         TRANSLUCENT("sodium:translucent", "translucent", true);
//$$
//$$         private final String groupName;
//$$         private final String layerName;
//$$         private final boolean translucent;
//$$
//$$         Pass(String groupName, String layerName, boolean translucent) {
//$$             this.groupName = groupName;
//$$             this.layerName = layerName;
//$$             this.translucent = translucent;
//$$         }
//$$
//$$         public String groupName() {
//$$             return this.groupName;
//$$         }
//$$
//$$         public String layerName() {
//$$             return this.layerName;
//$$         }
//$$
//$$         public boolean translucent() {
//$$             return this.translucent;
//$$         }
//$$     }
//$$ }
//#else
public final class SodiumPreparedSection {
    private SodiumPreparedSection() {
    }
}
//#endif
