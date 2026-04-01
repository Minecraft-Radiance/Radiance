package com.radiance.client.proxy.world;

import com.radiance.client.constant.Constants;
import com.radiance.client.vertex.PBRVertexFormatElements;
import com.radiance.client.vertex.PBRVertexFormats;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.BufferAllocator;
import org.lwjgl.system.MemoryUtil;

final class AtlasSafeGreedyMesher {

    private static final float EPSILON = 1.0e-4f;
    private static final int STRIDE = PBRVertexFormats.PBR_TRIANGLE.getVertexSizeByte();
    private static final int POS_OFFSET =
        PBRVertexFormats.PBR_TRIANGLE.getOffsetsByElementId()[PBRVertexFormatElements.PBR_POS.id()];
    private static final int UV_OFFSET = PBRVertexFormats.PBR_TRIANGLE.getOffsetsByElementId()[
        PBRVertexFormatElements.PBR_TEXTURE_UV.id()];

    private AtlasSafeGreedyMesher() {
    }

    static BuiltBuffer optimize(BuiltBuffer builtBuffer,
        RayTracingTuning.TerrainMeshingMode terrainMeshingMode, int maxMergeSpan) {
        BuiltBuffer.DrawParameters drawParameters = builtBuffer.getDrawParameters();
        if (terrainMeshingMode == RayTracingTuning.TerrainMeshingMode.LEGACY_QUADS
            || drawParameters.mode() != VertexFormat.DrawMode.QUADS
            || Constants.VertexFormats.getValue(drawParameters.format())
            != Constants.VertexFormats.PBR_TRIANGLE.getValue()
            || drawParameters.vertexCount() < 8) {
            return builtBuffer;
        }

        ByteBuffer byteBuffer = builtBuffer.getBuffer().slice().order(ByteOrder.nativeOrder());
        int quadCount = drawParameters.vertexCount() / 4;
        List<Quad> quads = new ArrayList<>(quadCount);
        for (int quadIndex = 0; quadIndex < quadCount; quadIndex++) {
            quads.add(Quad.parse(byteBuffer, quadIndex * 4 * STRIDE));
        }

        List<Quad> optimizedQuads = packGreedy(quads, terrainMeshingMode,
            Math.max(1, maxMergeSpan));
        if (optimizedQuads == quads) {
            return builtBuffer;
        }

        int totalSize = optimizedQuads.size() * 4 * STRIDE;
        BufferAllocator bufferAllocator = new BufferAllocator(totalSize);
        long address = bufferAllocator.allocate(totalSize);
        ByteBuffer output = MemoryUtil.memByteBuffer(address, totalSize);
        for (Quad quad : optimizedQuads) {
            quad.write(output);
        }
        output.flip();

        BufferAllocator.CloseableBuffer closeableBuffer = bufferAllocator.getAllocated();
        if (closeableBuffer == null) {
            bufferAllocator.close();
            throw new IllegalStateException("Failed to allocate merged chunk buffer");
        }

        return new BuiltBuffer(closeableBuffer, new BuiltBuffer.DrawParameters(
            drawParameters.format(),
            optimizedQuads.size() * 4,
            drawParameters.mode().getIndexCount(optimizedQuads.size() * 4),
            drawParameters.mode(),
            VertexFormat.IndexType.smallestFor(optimizedQuads.size() * 4)));
    }

    private static List<Quad> packGreedy(List<Quad> quads,
        RayTracingTuning.TerrainMeshingMode terrainMeshingMode, int maxMergeSpan) {
        Map<MergeGroupKey, MergeGroup> mergeGroups = new LinkedHashMap<>();
        List<Object> orderedOutputs = new ArrayList<>();
        for (Quad quad : quads) {
            if (!quad.mergeable) {
                orderedOutputs.add(quad);
                continue;
            }

            MergeGroupKey mergeGroupKey = quad.mergeGroupKey();
            MergeGroup mergeGroup = mergeGroups.get(mergeGroupKey);
            if (mergeGroup == null) {
                mergeGroup = new MergeGroup(quad);
                mergeGroups.put(mergeGroupKey, mergeGroup);
                orderedOutputs.add(mergeGroup);
            }
            mergeGroup.add(quad);
        }

        boolean changed = false;
        List<Quad> optimizedQuads = new ArrayList<>(quads.size());
        for (Object orderedOutput : orderedOutputs) {
            if (orderedOutput instanceof Quad quad) {
                optimizedQuads.add(quad);
                continue;
            }

            MergeGroup mergeGroup = (MergeGroup) orderedOutput;
            List<Quad> packedQuads = mergeGroup.pack(terrainMeshingMode, maxMergeSpan);
            if (packedQuads.size() < mergeGroup.quads.size()) {
                changed = true;
            }
            optimizedQuads.addAll(packedQuads);
        }
        return changed ? optimizedQuads : quads;
    }

    private static boolean approxEquals(float a, float b) {
        return Math.abs(a - b) <= EPSILON;
    }

    private static float readFloat(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).getFloat(offset);
    }

    private static void writeFloat(byte[] bytes, int offset, float value) {
        ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).putFloat(offset, value);
    }

    private static long quantize(float value) {
        return Math.round(value / EPSILON);
    }

    private static boolean spanWithinLimit(float startCoord, float endCoord, int maxMergeSpan) {
        return endCoord - startCoord <= maxMergeSpan + EPSILON;
    }

    private static final class MergeGroup {

        private final List<Quad> quads = new ArrayList<>();
        private final byte[] templateBytes;
        private final byte[] attributeSignature;
        private final TextureTransform textureTransform;
        private final int planeAxis;
        private final int uAxis;
        private final int vAxis;
        private final boolean flippedWinding;
        private final float planeCoord;

        private MergeGroup(Quad quad) {
            this.templateBytes = quad.templateBytes;
            this.attributeSignature = quad.attributeSignature;
            this.textureTransform = quad.textureTransform;
            this.planeAxis = quad.planeAxis;
            this.uAxis = quad.uAxis;
            this.vAxis = quad.vAxis;
            this.flippedWinding = quad.flippedWinding;
            this.planeCoord = quad.planeCoord;
        }

        private void add(Quad quad) {
            quads.add(quad);
        }

        private List<Quad> pack(RayTracingTuning.TerrainMeshingMode terrainMeshingMode,
            int maxMergeSpan) {
            if (quads.size() < 2) {
                return quads;
            }

            List<Float> uCoords = new ArrayList<>();
            List<Float> vCoords = new ArrayList<>();
            for (Quad quad : quads) {
                uCoords.add(quad.uMin);
                uCoords.add(quad.uMax);
                vCoords.add(quad.vMin);
                vCoords.add(quad.vMax);
            }
            uCoords = compactSortedCoords(uCoords);
            vCoords = compactSortedCoords(vCoords);
            if (uCoords.size() < 2 || vCoords.size() < 2) {
                return quads;
            }

            int uCellCount = uCoords.size() - 1;
            int vCellCount = vCoords.size() - 1;
            boolean[][] occupied = new boolean[vCellCount][uCellCount];
            for (Quad quad : quads) {
                int uStart = findCoordIndex(uCoords, quad.uMin);
                int uEnd = findCoordIndex(uCoords, quad.uMax);
                int vStart = findCoordIndex(vCoords, quad.vMin);
                int vEnd = findCoordIndex(vCoords, quad.vMax);
                if (uStart < 0 || uEnd < 0 || vStart < 0 || vEnd < 0 || uStart >= uEnd
                    || vStart >= vEnd) {
                    return quads;
                }

                for (int vIndex = vStart; vIndex < vEnd; vIndex++) {
                    for (int uIndex = uStart; uIndex < uEnd; uIndex++) {
                        if (occupied[vIndex][uIndex]) {
                            return quads;
                        }
                        occupied[vIndex][uIndex] = true;
                    }
                }
            }

            boolean[][] used = new boolean[vCellCount][uCellCount];
            List<Quad> packedQuads = new ArrayList<>();
            for (int vIndex = 0; vIndex < vCellCount; vIndex++) {
                for (int uIndex = 0; uIndex < uCellCount; uIndex++) {
                    if (!occupied[vIndex][uIndex] || used[vIndex][uIndex]) {
                        continue;
                    }

                    int width = growWidth(occupied, used, uCoords, uIndex, vIndex, maxMergeSpan);
                    int height = 1;
                    if (terrainMeshingMode == RayTracingTuning.TerrainMeshingMode.GREEDY_MESHING) {
                        height = growHeight(occupied, used, vCoords, uIndex, vIndex, width,
                            maxMergeSpan);
                    } else {
                        int verticalHeight = growVerticalStripHeight(occupied, used, vCoords, uIndex,
                            vIndex, maxMergeSpan);
                        float horizontalSpan = uCoords.get(uIndex + width) - uCoords.get(uIndex);
                        float verticalSpan =
                            vCoords.get(vIndex + verticalHeight) - vCoords.get(vIndex);
                        if (verticalHeight > 1 && verticalSpan > horizontalSpan + EPSILON) {
                            width = 1;
                            height = verticalHeight;
                        }
                    }

                    for (int row = vIndex; row < vIndex + height; row++) {
                        for (int column = uIndex; column < uIndex + width; column++) {
                            used[row][column] = true;
                        }
                    }

                    packedQuads.add(Quad.fromRectangle(templateBytes, attributeSignature,
                        textureTransform, planeAxis, uAxis, vAxis, flippedWinding, planeCoord,
                        uCoords.get(uIndex), uCoords.get(uIndex + width), vCoords.get(vIndex),
                        vCoords.get(vIndex + height)));
                }
            }
            return packedQuads;
        }

        private static int growWidth(boolean[][] occupied, boolean[][] used, List<Float> uCoords,
            int uIndex, int vIndex, int maxMergeSpan) {
            int width = 1;
            while (uIndex + width < occupied[vIndex].length
                && occupied[vIndex][uIndex + width]
                && !used[vIndex][uIndex + width]
                && spanWithinLimit(uCoords.get(uIndex), uCoords.get(uIndex + width + 1),
                maxMergeSpan)) {
                width++;
            }
            return width;
        }

        private static int growHeight(boolean[][] occupied, boolean[][] used, List<Float> vCoords,
            int uIndex, int vIndex, int width, int maxMergeSpan) {
            int height = 1;
            while (vIndex + height < occupied.length
                && canExtendHeight(occupied, used, uIndex, vIndex, width, height)
                && spanWithinLimit(vCoords.get(vIndex), vCoords.get(vIndex + height + 1),
                maxMergeSpan)) {
                height++;
            }
            return height;
        }

        private static int growVerticalStripHeight(boolean[][] occupied, boolean[][] used,
            List<Float> vCoords, int uIndex, int vIndex, int maxMergeSpan) {
            int height = 1;
            while (vIndex + height < occupied.length
                && occupied[vIndex + height][uIndex]
                && !used[vIndex + height][uIndex]
                && spanWithinLimit(vCoords.get(vIndex), vCoords.get(vIndex + height + 1),
                maxMergeSpan)) {
                height++;
            }
            return height;
        }

        private static boolean canExtendHeight(boolean[][] occupied, boolean[][] used, int uIndex,
            int vIndex, int width, int height) {
            int nextRow = vIndex + height;
            for (int column = uIndex; column < uIndex + width; column++) {
                if (!occupied[nextRow][column] || used[nextRow][column]) {
                    return false;
                }
            }
            return true;
        }

        private static List<Float> compactSortedCoords(List<Float> coords) {
            coords.sort(Float::compare);
            List<Float> compactCoords = new ArrayList<>(coords.size());
            for (Float coord : coords) {
                if (compactCoords.isEmpty() || !approxEquals(
                    compactCoords.get(compactCoords.size() - 1), coord)) {
                    compactCoords.add(coord);
                }
            }
            return compactCoords;
        }

        private static int findCoordIndex(List<Float> coords, float coord) {
            for (int index = 0; index < coords.size(); index++) {
                if (approxEquals(coords.get(index), coord)) {
                    return index;
                }
            }
            return -1;
        }
    }

    private static final class MergeGroupKey {

        private final int planeAxis;
        private final int uAxis;
        private final int vAxis;
        private final boolean flippedWinding;
        private final long planeCoordKey;
        private final long uFromUKey;
        private final long uFromVKey;
        private final long uBiasKey;
        private final long vFromUKey;
        private final long vFromVKey;
        private final long vBiasKey;
        private final byte[] attributeSignature;

        private MergeGroupKey(Quad quad) {
            this.planeAxis = quad.planeAxis;
            this.uAxis = quad.uAxis;
            this.vAxis = quad.vAxis;
            this.flippedWinding = quad.flippedWinding;
            this.planeCoordKey = quantize(quad.planeCoord);
            this.uFromUKey = quantize(quad.textureTransform.uFromU());
            this.uFromVKey = quantize(quad.textureTransform.uFromV());
            this.uBiasKey = quantize(quad.textureTransform.uBias());
            this.vFromUKey = quantize(quad.textureTransform.vFromU());
            this.vFromVKey = quantize(quad.textureTransform.vFromV());
            this.vBiasKey = quantize(quad.textureTransform.vBias());
            this.attributeSignature = quad.attributeSignature;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MergeGroupKey other)) {
                return false;
            }
            return planeAxis == other.planeAxis
                && uAxis == other.uAxis
                && vAxis == other.vAxis
                && flippedWinding == other.flippedWinding
                && planeCoordKey == other.planeCoordKey
                && uFromUKey == other.uFromUKey
                && uFromVKey == other.uFromVKey
                && uBiasKey == other.uBiasKey
                && vFromUKey == other.vFromUKey
                && vFromVKey == other.vFromVKey
                && vBiasKey == other.vBiasKey
                && Arrays.equals(attributeSignature, other.attributeSignature);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(planeAxis, uAxis, vAxis, flippedWinding, planeCoordKey,
                uFromUKey, uFromVKey, uBiasKey, vFromUKey, vFromVKey, vBiasKey);
            return 31 * result + Arrays.hashCode(attributeSignature);
        }
    }

    private static final class Quad {

        private final VertexData[] corners;
        private final byte[][] originalOrder;
        private final byte[] attributeSignature;
        private final byte[] templateBytes;
        private final TextureTransform textureTransform;
        private final int planeAxis;
        private final int uAxis;
        private final int vAxis;
        private final boolean flippedWinding;
        private final boolean mergeable;
        private final float planeCoord;
        private final float uMin;
        private final float uMax;
        private final float vMin;
        private final float vMax;

        private Quad(VertexData[] corners, byte[][] originalOrder, byte[] attributeSignature,
            byte[] templateBytes, TextureTransform textureTransform, int planeAxis, int uAxis,
            int vAxis, boolean flippedWinding, boolean mergeable, float planeCoord, float uMin,
            float uMax, float vMin, float vMax) {
            this.corners = corners;
            this.originalOrder = originalOrder;
            this.attributeSignature = attributeSignature;
            this.templateBytes = templateBytes;
            this.textureTransform = textureTransform;
            this.planeAxis = planeAxis;
            this.uAxis = uAxis;
            this.vAxis = vAxis;
            this.flippedWinding = flippedWinding;
            this.mergeable = mergeable;
            this.planeCoord = planeCoord;
            this.uMin = uMin;
            this.uMax = uMax;
            this.vMin = vMin;
            this.vMax = vMax;
        }

        private static Quad parse(ByteBuffer byteBuffer, int baseOffset) {
            byte[][] originalOrder = new byte[4][STRIDE];
            VertexData[] vertices = new VertexData[4];
            for (int i = 0; i < 4; i++) {
                byte[] bytes = new byte[STRIDE];
                int vertexOffset = baseOffset + i * STRIDE;
                ByteBuffer duplicate = byteBuffer.duplicate();
                duplicate.position(vertexOffset);
                duplicate.get(bytes);
                originalOrder[i] = bytes;
                vertices[i] = VertexData.from(bytes);
            }

            int planeAxis = -1;
            for (int axis = 0; axis < 3; axis++) {
                float value = vertices[0].position[axis];
                boolean constantAxis = true;
                for (int vertex = 1; vertex < 4; vertex++) {
                    if (!approxEquals(value, vertices[vertex].position[axis])) {
                        constantAxis = false;
                        break;
                    }
                }
                if (constantAxis) {
                    planeAxis = axis;
                    break;
                }
            }

            if (planeAxis < 0) {
                return createNonMergeable(vertices, originalOrder, new byte[0], null, null, -1, -1,
                    -1, false, 0, 0, 0, 0, 0);
            }

            int uAxis = (planeAxis + 1) % 3;
            int vAxis = (planeAxis + 2) % 3;
            float uMin = Float.POSITIVE_INFINITY;
            float uMax = Float.NEGATIVE_INFINITY;
            float vMin = Float.POSITIVE_INFINITY;
            float vMax = Float.NEGATIVE_INFINITY;
            for (VertexData vertexData : vertices) {
                uMin = Math.min(uMin, vertexData.position[uAxis]);
                uMax = Math.max(uMax, vertexData.position[uAxis]);
                vMin = Math.min(vMin, vertexData.position[vAxis]);
                vMax = Math.max(vMax, vertexData.position[vAxis]);
            }

            VertexData[] corners = new VertexData[4];
            for (VertexData vertexData : vertices) {
                int cornerIndex = -1;
                if (approxEquals(vertexData.position[uAxis], uMin)
                    && approxEquals(vertexData.position[vAxis], vMin)) {
                    cornerIndex = 0;
                } else if (approxEquals(vertexData.position[uAxis], uMax)
                    && approxEquals(vertexData.position[vAxis], vMin)) {
                    cornerIndex = 1;
                } else if (approxEquals(vertexData.position[uAxis], uMax)
                    && approxEquals(vertexData.position[vAxis], vMax)) {
                    cornerIndex = 2;
                } else if (approxEquals(vertexData.position[uAxis], uMin)
                    && approxEquals(vertexData.position[vAxis], vMax)) {
                    cornerIndex = 3;
                }

                if (cornerIndex < 0 || corners[cornerIndex] != null) {
                    return createNonMergeable(vertices, originalOrder, new byte[0], null, null,
                        planeAxis, uAxis, vAxis, false, vertices[0].position[planeAxis], uMin,
                        uMax, vMin, vMax);
                }
                corners[cornerIndex] = vertexData;
            }

            byte[] attributeSignature = corners[0].attributeSignature();
            boolean constantAttributes = attributeSignature.length > 0;
            if (constantAttributes) {
                for (int i = 1; i < corners.length; i++) {
                    if (!Arrays.equals(attributeSignature, corners[i].attributeSignature())) {
                        constantAttributes = false;
                        break;
                    }
                }
            }

            float cross = crossAxis(corners[0], corners[1], corners[3], planeAxis);
            float originalCross = crossAxis(vertices[0], vertices[1], vertices[2], planeAxis);
            boolean flippedWinding = Math.signum(cross) != Math.signum(originalCross);
            TextureTransform textureTransform = null;
            if (constantAttributes) {
                textureTransform = TextureTransform.from(corners, uMin, uMax, vMin, vMax);
            }
            if (!constantAttributes || textureTransform == null) {
                return createNonMergeable(vertices, originalOrder, attributeSignature, null, null,
                    planeAxis, uAxis, vAxis, flippedWinding, vertices[0].position[planeAxis], uMin,
                    uMax, vMin, vMax);
            }

            return new Quad(corners, null, attributeSignature,
                Arrays.copyOf(corners[0].bytes, corners[0].bytes.length), textureTransform,
                planeAxis, uAxis, vAxis, flippedWinding, true, vertices[0].position[planeAxis],
                uMin, uMax, vMin, vMax);
        }

        private static Quad fromRectangle(byte[] templateBytes, byte[] attributeSignature,
            TextureTransform textureTransform, int planeAxis, int uAxis, int vAxis,
            boolean flippedWinding, float planeCoord, float uMin, float uMax, float vMin,
            float vMax) {
            VertexData[] corners = new VertexData[]{
                VertexData.create(templateBytes, attributeSignature, textureTransform, planeAxis,
                    uAxis, vAxis, planeCoord, uMin, vMin),
                VertexData.create(templateBytes, attributeSignature, textureTransform, planeAxis,
                    uAxis, vAxis, planeCoord, uMax, vMin),
                VertexData.create(templateBytes, attributeSignature, textureTransform, planeAxis,
                    uAxis, vAxis, planeCoord, uMax, vMax),
                VertexData.create(templateBytes, attributeSignature, textureTransform, planeAxis,
                    uAxis, vAxis, planeCoord, uMin, vMax)
            };
            return new Quad(corners, null, attributeSignature,
                Arrays.copyOf(templateBytes, templateBytes.length), textureTransform, planeAxis,
                uAxis, vAxis, flippedWinding, true, planeCoord, uMin, uMax, vMin, vMax);
        }

        private static Quad createNonMergeable(VertexData[] corners, byte[][] originalOrder,
            byte[] attributeSignature, byte[] templateBytes, TextureTransform textureTransform,
            int planeAxis, int uAxis, int vAxis, boolean flippedWinding, float planeCoord,
            float uMin, float uMax, float vMin, float vMax) {
            return new Quad(corners, originalOrder, attributeSignature, templateBytes,
                textureTransform, planeAxis, uAxis, vAxis, flippedWinding, false, planeCoord, uMin,
                uMax, vMin, vMax);
        }

        private MergeGroupKey mergeGroupKey() {
            return new MergeGroupKey(this);
        }

        private void write(ByteBuffer output) {
            if (!mergeable) {
                for (byte[] bytes : originalOrder) {
                    output.put(bytes);
                }
                return;
            }

            int[] order = flippedWinding ? new int[]{0, 3, 2, 1} : new int[]{0, 1, 2, 3};
            for (int cornerIndex : order) {
                output.put(corners[cornerIndex].bytes);
            }
        }

        private static float crossAxis(VertexData a, VertexData b, VertexData c, int axis) {
            float ab0 = b.position[(axis + 1) % 3] - a.position[(axis + 1) % 3];
            float ab1 = b.position[(axis + 2) % 3] - a.position[(axis + 2) % 3];
            float ac0 = c.position[(axis + 1) % 3] - a.position[(axis + 1) % 3];
            float ac1 = c.position[(axis + 2) % 3] - a.position[(axis + 2) % 3];
            return ab0 * ac1 - ab1 * ac0;
        }
    }

    private record TextureTransform(float uFromU, float uFromV, float uBias, float vFromU,
                                    float vFromV, float vBias) {

        private static TextureTransform from(VertexData[] corners, float uMin, float uMax,
            float vMin, float vMax) {
            float width = uMax - uMin;
            float height = vMax - vMin;
            if (Math.abs(width) <= EPSILON || Math.abs(height) <= EPSILON) {
                return null;
            }

            float uFromU = (corners[1].u - corners[0].u) / width;
            float uFromV = (corners[3].u - corners[0].u) / height;
            float uBias = corners[0].u - uFromU * uMin - uFromV * vMin;
            float vFromU = (corners[1].v - corners[0].v) / width;
            float vFromV = (corners[3].v - corners[0].v) / height;
            float vBias = corners[0].v - vFromU * uMin - vFromV * vMin;
            TextureTransform textureTransform = new TextureTransform(uFromU, uFromV, uBias, vFromU,
                vFromV, vBias);
            if (!approxEquals(textureTransform.mapU(uMax, vMax), corners[2].u)
                || !approxEquals(textureTransform.mapV(uMax, vMax), corners[2].v)
                || !approxEquals(textureTransform.mapU(uMax, vMin), corners[1].u)
                || !approxEquals(textureTransform.mapV(uMax, vMin), corners[1].v)
                || !approxEquals(textureTransform.mapU(uMin, vMax), corners[3].u)
                || !approxEquals(textureTransform.mapV(uMin, vMax), corners[3].v)) {
                return null;
            }
            return textureTransform;
        }

        private float mapU(float uCoord, float vCoord) {
            return uFromU * uCoord + uFromV * vCoord + uBias;
        }

        private float mapV(float uCoord, float vCoord) {
            return vFromU * uCoord + vFromV * vCoord + vBias;
        }
    }

    private record VertexData(byte[] bytes, float[] position, float u, float v,
                              byte[] attributeSignature) {

        private static VertexData from(byte[] bytes) {
            float[] position = new float[]{
                readFloat(bytes, POS_OFFSET),
                readFloat(bytes, POS_OFFSET + 4),
                readFloat(bytes, POS_OFFSET + 8)
            };
            float u = readFloat(bytes, UV_OFFSET);
            float v = readFloat(bytes, UV_OFFSET + 4);
            return new VertexData(bytes, position, u, v, buildAttributeSignature(bytes));
        }

        private static VertexData create(byte[] templateBytes, byte[] attributeSignature,
            TextureTransform textureTransform, int planeAxis, int uAxis, int vAxis,
            float planeCoord, float uCoord, float vCoord) {
            byte[] bytes = Arrays.copyOf(templateBytes, templateBytes.length);
            float[] position = new float[3];
            position[planeAxis] = planeCoord;
            position[uAxis] = uCoord;
            position[vAxis] = vCoord;
            writeFloat(bytes, POS_OFFSET, position[0]);
            writeFloat(bytes, POS_OFFSET + 4, position[1]);
            writeFloat(bytes, POS_OFFSET + 8, position[2]);
            float u = textureTransform.mapU(uCoord, vCoord);
            float v = textureTransform.mapV(uCoord, vCoord);
            writeFloat(bytes, UV_OFFSET, u);
            writeFloat(bytes, UV_OFFSET + 4, v);
            return new VertexData(bytes, position, u, v, attributeSignature);
        }

        private static byte[] buildAttributeSignature(byte[] bytes) {
            byte[] signature = new byte[bytes.length - 20];
            int dst = 0;
            for (int src = 0; src < bytes.length; src++) {
                if (src >= POS_OFFSET && src < POS_OFFSET + 12) {
                    continue;
                }
                if (src >= UV_OFFSET && src < UV_OFFSET + 8) {
                    continue;
                }
                signature[dst++] = bytes[src];
            }
            return signature;
        }
    }
}
