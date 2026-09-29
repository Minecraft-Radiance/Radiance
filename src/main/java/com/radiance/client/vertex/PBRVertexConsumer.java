package com.radiance.client.vertex;

import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COORDINATE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_TEXTURE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_LIGHT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_OVERLAY_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POST_BASE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_ID;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_UV;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteOrder;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

public class PBRVertexConsumer implements VertexConsumer {

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final int USE_COLOR_LAYER_BIT = 1 << 0;
    private static final int USE_TEXTURE_BIT = 1 << 1;
    private static final int USE_OVERLAY_BIT = 1 << 2;
    private static final int USE_GLINT_BIT = 1 << 3;
    private static final int USE_NORM_BIT = 1 << 4;
    private static final int USE_LIGHT_BIT = 1 << 5;
    private static final int TEXT_MATERIAL_BIT = 1 << 7;
    private static final int ALPHA_MODE_SHIFT = 8;
    private static final int COORDINATE_SHIFT = 12;
    private static final int ALBEDO_EMISSION_SHIFT = 16;
    private static final int ALPHA_MODE_OPAQUE = 0;
    private static final int ALPHA_MODE_CUTOUT = 1;
    private static final int ALPHA_MODE_TRANSPARENT = 2;
    private static final int POST_TEXT_MODE_BACKGROUND = 1;
    private static final int POST_TEXT_MODE_INTENSITY = 2;
    private static final int POST_TEXT_MODE_RGBA = 3;
    private static final int POST_TEXT_MODE_BACKGROUND_SEE_THROUGH = 4;
    private static final int POST_TEXT_MODE_INTENSITY_SEE_THROUGH = 5;
    private static final int POST_TEXT_MODE_RGBA_SEE_THROUGH = 6;
    private static final int POST_TEXT_MODE_INTENSITY_POLYGON_OFFSET = 7;
    private static final int POST_TEXT_MODE_RGBA_POLYGON_OFFSET = 8;

    private final ByteBufferBuilder allocator;
    private final VertexFormat format;
    private final VertexFormat.Mode drawMode;

    private final int vertexSizeByte;
    private final int writableMask;
    private final int requiredMask;
    private final int[] offsetsByElementId;
    private long vertexPointer = -1L;
    private int vertexCount = 0;
    private int currentMask = 0;
    private boolean building = true;
    private int textureID;
    private final int alphaMode;
    private final boolean textMaterial;
    private float baseX = 0;
    private float baseY = 0;
    private float baseZ = 0;
    private int coordinate = 0;
    private boolean normalOffset = false;

    public PBRVertexConsumer(ByteBufferBuilder allocator, RenderType renderLayer) {
        this(allocator, VertexFormat.Mode.QUADS, PBRVertexFormats.ENTITY_PBR_TRIANGLE, renderLayer);
    }

    public static PBRVertexConsumer chunk(ByteBufferBuilder allocator, RenderType renderLayer) {
        return new PBRVertexConsumer(allocator, VertexFormat.Mode.QUADS,
            PBRVertexFormats.CHUNK_PBR_TRIANGLE, renderLayer);
    }

    private PBRVertexConsumer(ByteBufferBuilder allocator, VertexFormat.Mode drawMode,
        VertexFormat format, RenderType renderLayer) {
        this.allocator = allocator;
        this.drawMode = drawMode;
        this.format = format;

        this.vertexSizeByte = format.getVertexSize();
        this.writableMask = format.getElementsMask() & ~PBR_POS.mask();
        this.requiredMask = 0;
        this.offsetsByElementId = format.getOffsetsByElement();

        if (this.vertexSizeByte != 64 && this.vertexSizeByte != 40) {
            throw new IllegalStateException(
                "PBR vertex stride must be 64 or 40, got " + this.vertexSizeByte);
        }
        if (!format.contains(PBR_POS)) {
            throw new IllegalArgumentException("PBR format must contain POSITION element");
        }

        if (renderLayer instanceof RenderType.CompositeRenderType) {
            ResourceLocation
                identifier =
                ((RenderType.CompositeRenderType) renderLayer).state.textureState.cutoutTexture()
                    .orElse(MissingTextureAtlasSprite.getLocation());
            textureID =
                Minecraft.getInstance()
                    .getTextureManager()
                    .getTexture(identifier)
                    .getId();
        }
        this.alphaMode = getAlphaMode(renderLayer);
        this.textMaterial = renderLayer instanceof RenderType.CompositeRenderType multiPhase
            && getPostTextMode(multiPhase.name) != ALPHA_MODE_OPAQUE;
    }

    private static void putInt(long ptr, int v) {
        if (LITTLE_ENDIAN) {
            MemoryUtil.memPutInt(ptr, v);
        } else {
            MemoryUtil.memPutShort(ptr, (short) (v & 0xFFFF));
            MemoryUtil.memPutShort(ptr + 2L, (short) ((v >>> 16) & 0xFFFF));
        }
    }

    private static int getInt(long ptr) {
        if (LITTLE_ENDIAN) {
            return MemoryUtil.memGetInt(ptr);
        }
        return (MemoryUtil.memGetShort(ptr) & 0xFFFF)
            | ((MemoryUtil.memGetShort(ptr + 2L) & 0xFFFF) << 16);
    }

    private static int packU16x2(int u, int v) {
        return (u & 0xFFFF) | ((v & 0xFFFF) << 16);
    }

    private static int packColorRGBA8(int red, int green, int blue, int alpha) {
        return (red & 0xFF) | ((green & 0xFF) << 8) | ((blue & 0xFF) << 16)
            | ((alpha & 0xFF) << 24);
    }

    private static int floatToHalfBits(float value) {
        int bits = Float.floatToRawIntBits(value);
        int sign = (bits >>> 16) & 0x8000;
        int exponent = (bits >>> 23) & 0xFF;
        int mantissa = bits & 0x7FFFFF;

        if (exponent == 0xFF) {
            return sign | (mantissa == 0 ? 0x7C00 : 0x7E00);
        }

        int halfExponent = exponent - 127 + 15;
        if (halfExponent >= 0x1F) {
            return sign | 0x7C00;
        }
        if (halfExponent <= 0) {
            if (halfExponent < -10) {
                return sign;
            }
            mantissa |= 0x800000;
            int shift = 14 - halfExponent;
            int halfMantissa = mantissa >> shift;
            if (((mantissa >> (shift - 1)) & 1) != 0) {
                halfMantissa++;
            }
            return sign | halfMantissa;
        }

        int halfMantissa = mantissa >> 13;
        if ((mantissa & 0x1000) != 0) {
            halfMantissa++;
            if ((halfMantissa & 0x400) != 0) {
                halfMantissa = 0;
                halfExponent++;
                if (halfExponent >= 0x1F) {
                    return sign | 0x7C00;
                }
            }
        }
        return sign | (halfExponent << 10) | (halfMantissa & 0x3FF);
    }

    private static int packHalf2(float x, float y) {
        return (floatToHalfBits(x) & 0xFFFF) | ((floatToHalfBits(y) & 0xFFFF) << 16);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float octSignNotZero(float value) {
        return value >= 0.0f ? 1.0f : -1.0f;
    }

    private static int packSnorm16(float value) {
        return Math.round(clamp(value, -1.0f, 1.0f) * 32767.0f) & 0xFFFF;
    }

    private static int packNormalOct(float x, float y, float z) {
        float lengthSquared = x * x + y * y + z * z;
        if (lengthSquared <= 0.0f || Float.isNaN(lengthSquared)) {
            return 0;
        }

        float inverseLength = 1.0f / (float) Math.sqrt(lengthSquared);
        x *= inverseLength;
        y *= inverseLength;
        z *= inverseLength;

        float inverseOctLength = 1.0f / Math.max(Math.abs(x) + Math.abs(y) + Math.abs(z), 1e-8f);
        x *= inverseOctLength;
        y *= inverseOctLength;

        if (z < 0.0f) {
            float oldX = x;
            x = (1.0f - Math.abs(y)) * octSignNotZero(oldX);
            y = (1.0f - Math.abs(oldX)) * octSignNotZero(y);
        }

        return packSnorm16(x) | (packSnorm16(y) << 16);
    }

    private static int getAlphaMode(RenderType renderLayer) {
        if (!(renderLayer instanceof RenderType.CompositeRenderType multiPhase)) {
            return ALPHA_MODE_OPAQUE;
        }

        int postTextMode = getPostTextMode(multiPhase.name);
        if (postTextMode != ALPHA_MODE_OPAQUE) {
            return postTextMode;
        }

        if (multiPhase.name.contains("solid")) {
            return ALPHA_MODE_OPAQUE;
        }

        if (multiPhase.name.contains("cutout")) {
            return ALPHA_MODE_CUTOUT;
        }

        if (RenderStateShard.NO_TRANSPARENCY.equals(multiPhase.state.transparencyState)) {
            return ALPHA_MODE_CUTOUT;
        }

        return ALPHA_MODE_TRANSPARENT;
    }

    private static int getPostTextMode(String layerName) {
        return switch (layerName) {
            case "text_background" -> POST_TEXT_MODE_BACKGROUND;
            case "text_intensity" -> POST_TEXT_MODE_INTENSITY;
            case "text" -> POST_TEXT_MODE_RGBA;
            case "text_background_see_through" -> POST_TEXT_MODE_BACKGROUND_SEE_THROUGH;
            case "text_intensity_see_through" -> POST_TEXT_MODE_INTENSITY_SEE_THROUGH;
            case "text_see_through" -> POST_TEXT_MODE_RGBA_SEE_THROUGH;
            case "text_intensity_polygon_offset" -> POST_TEXT_MODE_INTENSITY_POLYGON_OFFSET;
            case "text_polygon_offset" -> POST_TEXT_MODE_RGBA_POLYGON_OFFSET;
            default -> ALPHA_MODE_OPAQUE;
        };
    }

    public VertexFormat getFormat() {
        return this.format;
    }

    public int getVertexCount() {
        return this.vertexCount;
    }

    public void setBase(float x, float y, float z) {
        this.baseX = x;
        this.baseY = y;
        this.baseZ = z;
    }

    public void setEntityBuildContext(int coordinate, boolean normalOffset, float baseX,
        float baseY, float baseZ) {
        this.coordinate = coordinate;
        this.normalOffset = normalOffset;
        setBase(baseX, baseY, baseZ);
    }

    private void ensureBuilding() {
        if (!building) {
            throw new IllegalStateException("Not building!");
        }
    }

    @Nullable
    public MeshData endNullable() {
        ensureBuilding();
        endVertex();
        MeshData built = build();
        building = false;
        vertexPointer = -1L;
        return built;
    }

    public MeshData end() {
        MeshData built = endNullable();
        if (built == null) {
            throw new IllegalStateException("PBRBufferBuilder was empty");
        }
        return built;
    }

    @Nullable
    private MeshData build() {
        if (vertexCount == 0) {
            return null;
        }

        ByteBufferBuilder.Result buf = allocator.build();
        if (buf == null) {
            return null;
        }

        int indexCount = drawMode.indexCount(vertexCount);
        VertexFormat.IndexType indexType = VertexFormat.IndexType.least(vertexCount);
        return new MeshData(buf,
            new MeshData.DrawState(format, vertexCount, indexCount, drawMode, indexType));
    }

    private long beginVertex() {
        ensureBuilding();
        endVertex();

        vertexCount++;
        long ptr = allocator.reserve(vertexSizeByte);
        vertexPointer = ptr;
        MemoryUtil.memSet(ptr, 0, vertexSizeByte);

        if (this.textureID != 0) {
            int off = this.offsetsByElementId[PBR_TEXTURE_ID.id()];
            if (off >= 0) {
                putInt(ptr + off, this.textureID);
            }
        }

        int offCoordinate = this.offsetsByElementId[PBR_COORDINATE.id()];
        if (offCoordinate >= 0) {
            putInt(ptr + offCoordinate,
                ((this.alphaMode & 0xF) << ALPHA_MODE_SHIFT)
                    | ((this.coordinate & 0xF) << COORDINATE_SHIFT)
                    | (this.textMaterial ? TEXT_MATERIAL_BIT : 0));
        }

        int offBase = this.offsetsByElementId[PBR_POST_BASE.id()];
        if (offBase >= 0) {
            MemoryUtil.memPutFloat(ptr + offBase, baseX);
            MemoryUtil.memPutFloat(ptr + offBase + 4L, baseY);
            MemoryUtil.memPutFloat(ptr + offBase + 8L, baseZ);
        }

        return ptr;
    }

    private long beginVertex(int glintTextureID) {
        ensureBuilding();
        endVertex();

        vertexCount++;
        long ptr = allocator.reserve(vertexSizeByte);
        vertexPointer = ptr;
        MemoryUtil.memSet(ptr, 0, vertexSizeByte);

        if (this.textureID != 0) {
            int off = this.offsetsByElementId[PBR_TEXTURE_ID.id()];
            if (off >= 0) {
                putInt(ptr + off, this.textureID);
            }
        }

        int offCoordinate = this.offsetsByElementId[PBR_COORDINATE.id()];
        if (offCoordinate >= 0) {
            putInt(ptr + offCoordinate,
                ((this.alphaMode & 0xF) << ALPHA_MODE_SHIFT)
                    | ((this.coordinate & 0xF) << COORDINATE_SHIFT)
                    | (this.textMaterial ? TEXT_MATERIAL_BIT : 0));
        }

        int offBase = this.offsetsByElementId[PBR_POST_BASE.id()];
        if (offBase >= 0) {
            MemoryUtil.memPutFloat(ptr + offBase, baseX);
            MemoryUtil.memPutFloat(ptr + offBase + 4L, baseY);
            MemoryUtil.memPutFloat(ptr + offBase + 8L, baseZ);
        }

        if (glintTextureID != 0) {
            int off = this.offsetsByElementId[PBR_GLINT_TEXTURE.id()];
            if (off >= 0) {
                putInt(ptr + off, glintTextureID);
            }
        }

        return ptr;
    }

    private long beginElement(VertexFormatElement element) {
        int mask = currentMask;
        int bit = element.mask();
        if ((mask & bit) == 0) {
            return -1L;
        }

        currentMask = mask & ~bit;

        long base = vertexPointer;
        if (base == -1L) {
            throw new IllegalStateException("Not currently building vertex");
        }

        int id = element.id();
        int off = offsetsByElementId[id];
        if (off < 0) {
            throw new IllegalStateException(
                "Element present in mask but not in format: " + element);
        }
        return base + off;
    }

    private long packedDataPointer() {
        long base = vertexPointer;
        if (base == -1L) {
            throw new IllegalStateException("Not currently building vertex");
        }

        int off = offsetsByElementId[PBR_COORDINATE.id()];
        if (off < 0) {
            throw new IllegalStateException("PBR format must contain packed data element");
        }
        return base + off;
    }

    private void setPackedFlag(int flag) {
        long p = packedDataPointer();
        putInt(p, getInt(p) | flag);
    }

    private void setPackedAlbedoEmission(float emission) {
        long p = packedDataPointer();
        int packedData = getInt(p);
        int half = floatToHalfBits(Math.max(emission, 0.0f));
        putInt(p, (packedData & 0x0000FFFF) | (half << ALBEDO_EMISSION_SHIFT));
    }

    private void endVertex() {
        if (vertexCount == 0) {
            return;
        }

        int missing = currentMask & requiredMask;
        if (missing != 0) {
            String
                s =
                VertexFormatElement.elementsFromMask(currentMask)
                    .map(format::getElementName)
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Missing elements in vertex: " + s);
        }
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        long base = beginVertex();
        currentMask = writableMask;

        int posOff = offsetsByElementId[PBR_POS.id()];
        long p = base + posOff;

        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
            MemoryUtil.memPutFloat(p, 0);
            MemoryUtil.memPutFloat(p + 4L, 0);
            MemoryUtil.memPutFloat(p + 8L, 0);
        } else {
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4L, y);
            MemoryUtil.memPutFloat(p + 8L, z);
        }

        return this;
    }

    public VertexConsumer vertex(float x, float y, float z, int glintTextureID) {
        long base = beginVertex(glintTextureID);
        currentMask = writableMask;

        int posOff = offsetsByElementId[PBR_POS.id()];
        long p = base + posOff;

        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
            MemoryUtil.memPutFloat(p, 0);
            MemoryUtil.memPutFloat(p + 4L, 0);
            MemoryUtil.memPutFloat(p + 8L, 0);
        } else {
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4L, y);
            MemoryUtil.memPutFloat(p + 8L, z);
        }

        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        setPackedFlag(USE_COLOR_LAYER_BIT);

        long p = beginElement(PBR_COLOR_LAYER);
        if (p != -1L) {
            putInt(p, packColorRGBA8(red, green, blue, alpha));
        }
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        setPackedFlag(USE_TEXTURE_BIT);

        long p = beginElement(PBR_TEXTURE_UV);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, u);
            MemoryUtil.memPutFloat(p + 4L, v);
        }
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        long p = beginElement(PBR_OVERLAY_UV);
        if (p != -1L) {
            setPackedFlag(USE_OVERLAY_BIT);
            putInt(p, packU16x2(u, v));
        }
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        setPackedFlag(USE_LIGHT_BIT);

        long p = beginElement(PBR_LIGHT_UV);
        if (p != -1L) {
            putInt(p, packU16x2(u, v));
        }
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        setPackedFlag(USE_NORM_BIT);

        long p = beginElement(PBR_NORM);
        if (p != -1L) {
            putInt(p, packNormalOct(x, y, z));
        }

        if (normalOffset) {
            applyNormalOffset(x, y, z);
        }
        return this;
    }

    private void applyNormalOffset(float x, float y, float z) {
        long base = vertexPointer;
        if (base == -1L) {
            return;
        }

        float lengthSquared = x * x + y * y + z * z;
        if (lengthSquared <= 0.0f || Float.isNaN(lengthSquared)) {
            return;
        }

        int posOff = offsetsByElementId[PBR_POS.id()];
        if (posOff < 0) {
            return;
        }

        float scale = 0.00001f / (float) Math.sqrt(lengthSquared);
        long pos = base + posOff;
        MemoryUtil.memPutFloat(pos, MemoryUtil.memGetFloat(pos) + x * scale);
        MemoryUtil.memPutFloat(pos + 4L, MemoryUtil.memGetFloat(pos + 4L) + y * scale);
        MemoryUtil.memPutFloat(pos + 8L, MemoryUtil.memGetFloat(pos + 8L) + z * scale);
    }

    public VertexConsumer albedoEmission(float emission) {
        setPackedAlbedoEmission(emission);
        return this;
    }

    public static boolean canUseExternalPbr(MeshData builtBuffer) {
        MeshData.DrawState drawParameters = builtBuffer.drawState();
        return drawParameters.format() == PBRVertexFormats.ENTITY_PBR_TRIANGLE &&
            drawParameters.mode() == VertexFormat.Mode.QUADS;
    }

    public static class GLint implements VertexConsumer {

        private final PBRVertexConsumer delegate;
        private int glintTextureID;

        public GLint(PBRVertexConsumer delegate, RenderType glintRenderLayer) {
            this.delegate = delegate;
            if (glintRenderLayer instanceof RenderType.CompositeRenderType) {
                ResourceLocation
                    identifier =
                    ((RenderType.CompositeRenderType) glintRenderLayer).state.textureState.cutoutTexture()
                        .orElse(MissingTextureAtlasSprite.getLocation());
                glintTextureID =
                    Minecraft.getInstance()
                        .getTextureManager()
                        .getTexture(identifier)
                        .getId();
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.vertex(x, y, z, this.glintTextureID);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);

            long p = delegate.beginElement(PBR_GLINT_UV);
            if (p != -1L) {
                delegate.setPackedFlag(USE_GLINT_BIT);
                putInt(p, packHalf2(u, v));
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    public static class GLintOverlay implements VertexConsumer {

        private final PBRVertexConsumer delegate;
        private final Matrix4f inverseTextureMatrix;
        private final Matrix3f inverseNormalMatrix;
        private final float textureScale;
        private final Vector3f normal = new Vector3f();
        private final Vector3f pos = new Vector3f();
        private int glintTextureID;
        private float x;
        private float y;
        private float z;

        public GLintOverlay(PBRVertexConsumer delegate, RenderType glintRenderLayer,
            PoseStack.Pose matrix, float textureScale) {
            this.delegate = delegate;
            if (glintRenderLayer instanceof RenderType.CompositeRenderType) {
                ResourceLocation
                    identifier =
                    ((RenderType.CompositeRenderType) glintRenderLayer).state.textureState.cutoutTexture()
                        .orElse(MissingTextureAtlasSprite.getLocation());
                glintTextureID =
                    Minecraft.getInstance()
                        .getTextureManager()
                        .getTexture(identifier)
                        .getId();
            }

            this.inverseTextureMatrix = new Matrix4f(matrix.pose()).invert();
            this.inverseNormalMatrix = new Matrix3f(matrix.normal()).invert();
            this.textureScale = textureScale;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            delegate.vertex(x, y, z, this.glintTextureID);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            Vector3f vector3f = this.inverseNormalMatrix.transform(x, y, z, this.pos);
            Direction direction = Direction.getNearest(vector3f.x(), vector3f.y(), vector3f.z());
            Vector3f vector3f2 = this.inverseTextureMatrix.transformPosition(this.x, this.y, this.z,
                this.normal);
            vector3f2.rotateY((float) Math.PI);
            vector3f2.rotateX((float) (-Math.PI / 2));
            vector3f2.rotate(direction.getRotation());

            long p = delegate.beginElement(PBR_GLINT_UV);
            if (p != -1L) {
                delegate.setPackedFlag(USE_GLINT_BIT);
                putInt(p, packHalf2(-vector3f2.x() * this.textureScale,
                    -vector3f2.y() * this.textureScale));
            }
            return this;
        }
    }
}
