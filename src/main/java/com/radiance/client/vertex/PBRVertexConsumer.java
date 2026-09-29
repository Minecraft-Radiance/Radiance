package com.radiance.client.vertex;

import com.radiance.client.render.BuiltBuffer;
import com.radiance.client.render.DirectBufferAllocator;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
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

    private static final int ENTITY_STRIDE = 64;
    private static final int CHUNK_STRIDE = 40;
    private static final int POS_OFFSET = 0;
    private static final int NORM_OFFSET = 12;
    private static final int TEXTURE_UV_OFFSET = 16;
    private static final int COLOR_LAYER_OFFSET = 24;
    private static final int LIGHT_UV_OFFSET = 28;
    private static final int ENTITY_OVERLAY_UV_OFFSET = 32;
    private static final int ENTITY_GLINT_UV_OFFSET = 36;
    private static final int ENTITY_TEXTURE_ID_OFFSET = 40;
    private static final int ENTITY_GLINT_TEXTURE_OFFSET = 44;
    private static final int ENTITY_POST_BASE_OFFSET = 48;
    private static final int ENTITY_PACKED_DATA_OFFSET = 60;
    private static final int CHUNK_TEXTURE_ID_OFFSET = 32;
    private static final int CHUNK_PACKED_DATA_OFFSET = 36;

    private final DirectBufferAllocator allocator;
    private final VertexFormat format;
    private final VertexFormat.DrawMode drawMode;
    private final int stride;
    private final boolean entityFormat;
    private int vertexCount = 0;
    private boolean building = true;
    private long vertexPointer = -1L;
    private long lastVertexPointer = -1L;
    private int textureID;
    private final int alphaMode;
    private final boolean textMaterial;
    private float baseX = 0;
    private float baseY = 0;
    private float baseZ = 0;
    private int coordinate = 0;
    private boolean normalOffset = false;
    private boolean hasFixedColor = false;
    private int fixedRed;
    private int fixedGreen;
    private int fixedBlue;
    private int fixedAlpha;

    public PBRVertexConsumer(int initialCapacity, RenderLayer renderLayer) {
        this(initialCapacity, VertexFormat.DrawMode.QUADS, PBRVertexFormats.ENTITY_PBR_TRIANGLE,
            true, renderLayer);
    }

    public static PBRVertexConsumer chunk(int initialCapacity, RenderLayer renderLayer) {
        return new PBRVertexConsumer(initialCapacity, VertexFormat.DrawMode.QUADS,
            PBRVertexFormats.CHUNK_PBR_TRIANGLE, false, renderLayer);
    }

    private PBRVertexConsumer(int initialCapacity, VertexFormat.DrawMode drawMode,
        VertexFormat format, boolean entityFormat, RenderLayer renderLayer) {
        this.allocator = new DirectBufferAllocator(initialCapacity);
        this.drawMode = drawMode;
        this.format = format;
        this.entityFormat = entityFormat;
        this.stride = entityFormat ? ENTITY_STRIDE : CHUNK_STRIDE;

        if (format.getVertexSizeByte() != this.stride) {
            throw new IllegalStateException(
                "PBR vertex stride must be " + this.stride + ", got "
                    + format.getVertexSizeByte());
        }

        if (renderLayer instanceof RenderLayer.MultiPhase) {
            Identifier identifier = ((RenderLayer.MultiPhase) renderLayer).phases.texture.getId()
                .orElse(MissingSprite.getMissingSpriteId());
            this.textureID = MinecraftClient.getInstance()
                .getTextureManager()
                .getTexture(identifier)
                .getGlId();
        }
        this.alphaMode = getAlphaMode(renderLayer);
        this.textMaterial = renderLayer instanceof RenderLayer.MultiPhase multiPhase
            && getPostTextMode(multiPhase.name) != ALPHA_MODE_OPAQUE;
    }

    private static void putInt(long ptr, int value) {
        if (LITTLE_ENDIAN) {
            MemoryUtil.memPutInt(ptr, value);
        } else {
            MemoryUtil.memPutShort(ptr, (short) (value & 0xFFFF));
            MemoryUtil.memPutShort(ptr + 2L, (short) ((value >>> 16) & 0xFFFF));
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

    private static int getAlphaMode(RenderLayer renderLayer) {
        if (!(renderLayer instanceof RenderLayer.MultiPhase multiPhase)) {
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

        if (RenderPhase.NO_TRANSPARENCY == multiPhase.phases.transparency) {
            return ALPHA_MODE_CUTOUT;
        }

        return ALPHA_MODE_TRANSPARENT;
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
        if (!this.building) {
            throw new IllegalStateException("Not building");
        }
    }

    private void ensureVertexStarted() {
        if (this.vertexPointer == -1L) {
            throw new IllegalStateException("No active vertex");
        }
    }

    private int textureIdOffset() {
        return this.entityFormat ? ENTITY_TEXTURE_ID_OFFSET : CHUNK_TEXTURE_ID_OFFSET;
    }

    private int packedDataOffset() {
        return this.entityFormat ? ENTITY_PACKED_DATA_OFFSET : CHUNK_PACKED_DATA_OFFSET;
    }

    private int initialPackedData() {
        return ((this.alphaMode & 0xF) << ALPHA_MODE_SHIFT)
            | ((this.coordinate & 0xF) << COORDINATE_SHIFT)
            | (this.textMaterial ? TEXT_MATERIAL_BIT : 0);
    }

    private long beginVertex(float x, float y, float z, int glintTextureID) {
        ensureBuilding();
        endVertex();

        this.vertexCount++;
        this.vertexPointer = this.allocator.allocate(this.stride);
        this.lastVertexPointer = this.vertexPointer;
        MemoryUtil.memSet(this.vertexPointer, 0, this.stride);

        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
            MemoryUtil.memPutFloat(this.vertexPointer + POS_OFFSET, 0);
            MemoryUtil.memPutFloat(this.vertexPointer + POS_OFFSET + 4L, 0);
            MemoryUtil.memPutFloat(this.vertexPointer + POS_OFFSET + 8L, 0);
        } else {
            MemoryUtil.memPutFloat(this.vertexPointer + POS_OFFSET, x);
            MemoryUtil.memPutFloat(this.vertexPointer + POS_OFFSET + 4L, y);
            MemoryUtil.memPutFloat(this.vertexPointer + POS_OFFSET + 8L, z);
        }

        if (this.textureID != 0) {
            putInt(this.vertexPointer + textureIdOffset(), this.textureID);
        }
        if (this.entityFormat && glintTextureID != 0) {
            putInt(this.vertexPointer + ENTITY_GLINT_TEXTURE_OFFSET, glintTextureID);
        }

        if (this.entityFormat) {
            MemoryUtil.memPutFloat(this.vertexPointer + ENTITY_POST_BASE_OFFSET, this.baseX);
            MemoryUtil.memPutFloat(this.vertexPointer + ENTITY_POST_BASE_OFFSET + 4L, this.baseY);
            MemoryUtil.memPutFloat(this.vertexPointer + ENTITY_POST_BASE_OFFSET + 8L, this.baseZ);
        }
        putInt(this.vertexPointer + packedDataOffset(), initialPackedData());

        if (this.hasFixedColor) {
            putColor(this.fixedRed, this.fixedGreen, this.fixedBlue, this.fixedAlpha);
        }

        return this.vertexPointer;
    }

    private void endVertex() {
        if (this.vertexPointer != -1L) {
            this.vertexPointer = -1L;
        }
    }

    @Nullable
    public BuiltBuffer endNullable() {
        ensureBuilding();
        endVertex();
        this.building = false;
        return build();
    }

    public BuiltBuffer end() {
        BuiltBuffer built = endNullable();
        if (built == null) {
            throw new IllegalStateException("PBR vertex buffer is empty");
        }
        return built;
    }

    @Nullable
    private BuiltBuffer build() {
        if (this.vertexCount == 0) {
            this.allocator.close();
            return null;
        }

        ByteBuffer buffer = this.allocator.takeBuffer();
        if (buffer == null) {
            this.allocator.close();
            return null;
        }

        int indexCount = this.drawMode.getIndexCount(this.vertexCount);
        VertexFormat.IndexType indexType = VertexFormat.IndexType.smallestFor(this.vertexCount);
        return new BuiltBuffer(buffer, null,
            new BuiltBuffer.DrawParameters(this.format, this.vertexCount, indexCount,
                this.drawMode, indexType), this.allocator::close);
    }

    private void setPackedFlag(int flag) {
        ensureVertexStarted();
        long packedDataPointer = this.vertexPointer + packedDataOffset();
        putInt(packedDataPointer, getInt(packedDataPointer) | flag);
    }

    private void putColor(int red, int green, int blue, int alpha) {
        setPackedFlag(USE_COLOR_LAYER_BIT);
        putInt(this.vertexPointer + COLOR_LAYER_OFFSET, packColorRGBA8(red, green, blue, alpha));
    }

    private void putTexture(float u, float v) {
        setPackedFlag(USE_TEXTURE_BIT);
        MemoryUtil.memPutFloat(this.vertexPointer + TEXTURE_UV_OFFSET, u);
        MemoryUtil.memPutFloat(this.vertexPointer + TEXTURE_UV_OFFSET + 4L, v);
    }

    private void putOverlay(int u, int v) {
        ensureVertexStarted();
        if (!this.entityFormat) {
            return;
        }
        setPackedFlag(USE_OVERLAY_BIT);
        putInt(this.vertexPointer + ENTITY_OVERLAY_UV_OFFSET, packU16x2(u, v));
    }

    private void putLight(int u, int v) {
        setPackedFlag(USE_LIGHT_BIT);
        putInt(this.vertexPointer + LIGHT_UV_OFFSET, packU16x2(u, v));
    }

    private void putNormal(float x, float y, float z) {
        setPackedFlag(USE_NORM_BIT);
        putInt(this.vertexPointer + NORM_OFFSET, packNormalOct(x, y, z));

        if (this.normalOffset) {
            applyNormalOffset(x, y, z);
        }
    }

    private void applyNormalOffset(float x, float y, float z) {
        long base = this.vertexPointer;
        if (base == -1L) {
            return;
        }

        float lengthSquared = x * x + y * y + z * z;
        if (lengthSquared <= 0.0f || Float.isNaN(lengthSquared)) {
            return;
        }

        float scale = 0.00001f / (float) Math.sqrt(lengthSquared);
        MemoryUtil.memPutFloat(base + POS_OFFSET, MemoryUtil.memGetFloat(base + POS_OFFSET) + x * scale);
        MemoryUtil.memPutFloat(base + POS_OFFSET + 4L,
            MemoryUtil.memGetFloat(base + POS_OFFSET + 4L) + y * scale);
        MemoryUtil.memPutFloat(base + POS_OFFSET + 8L,
            MemoryUtil.memGetFloat(base + POS_OFFSET + 8L) + z * scale);
    }

    private void putGlint(float u, float v) {
        ensureVertexStarted();
        if (!this.entityFormat) {
            return;
        }
        setPackedFlag(USE_GLINT_BIT);
        putInt(this.vertexPointer + ENTITY_GLINT_UV_OFFSET, packHalf2(u, v));
    }

    private void putAlbedoEmission(long pointer, float emission) {
        int packedDataOffset = packedDataOffset();
        int packedData = getInt(pointer + packedDataOffset);
        int packedHalf = floatToHalfBits(Math.max(emission, 0.0f));
        putInt(pointer + packedDataOffset,
            (packedData & 0x0000FFFF) | (packedHalf << ALBEDO_EMISSION_SHIFT));
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        beginVertex((float) x, (float) y, (float) z, 0);
        return this;
    }

    public VertexConsumer vertex(float x, float y, float z, int glintTextureID) {
        beginVertex(x, y, z, glintTextureID);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        if (this.hasFixedColor) {
            throw new IllegalStateException("Color is fixed");
        }
        putColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        putTexture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        putOverlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        putLight(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        putNormal(x, y, z);
        return this;
    }

    public VertexConsumer albedoEmission(float emission) {
        if (this.vertexPointer != -1L) {
            putAlbedoEmission(this.vertexPointer, emission);
            return this;
        }

        if (this.lastVertexPointer == -1L) {
            throw new IllegalStateException("No active vertex");
        }

        int verticesToUpdate = Math.min(this.vertexCount, 4);
        for (int i = 0; i < verticesToUpdate; i++) {
            long pointer = this.lastVertexPointer - (long) (verticesToUpdate - 1 - i) * this.stride;
            putAlbedoEmission(pointer, emission);
        }
        return this;
    }

    @Override
    public void next() {
        endVertex();
    }

    @Override
    public void fixedColor(int red, int green, int blue, int alpha) {
        this.hasFixedColor = true;
        this.fixedRed = red;
        this.fixedGreen = green;
        this.fixedBlue = blue;
        this.fixedAlpha = alpha;
    }

    @Override
    public void unfixColor() {
        this.hasFixedColor = false;
    }

    public void close() {
        if (this.building) {
            this.allocator.close();
        }
    }

    public static boolean canUseExternalPbr(BuiltBuffer builtBuffer) {
        BuiltBuffer.DrawParameters drawParameters = builtBuffer.getDrawParameters();
        return drawParameters.format() == PBRVertexFormats.ENTITY_PBR_TRIANGLE
            && drawParameters.mode() == VertexFormat.DrawMode.QUADS;
    }

    public static class GLint implements VertexConsumer {

        private final PBRVertexConsumer delegate;
        private final int glintTextureID;

        public GLint(PBRVertexConsumer delegate, RenderLayer glintRenderLayer) {
            this.delegate = delegate;
            if (glintRenderLayer instanceof RenderLayer.MultiPhase) {
                Identifier identifier = ((RenderLayer.MultiPhase) glintRenderLayer).phases.texture.getId()
                    .orElse(MissingSprite.getMissingSpriteId());
                this.glintTextureID = MinecraftClient.getInstance()
                    .getTextureManager()
                    .getTexture(identifier)
                    .getGlId();
            } else {
                this.glintTextureID = 0;
            }
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.delegate.vertex((float) x, (float) y, (float) z, this.glintTextureID);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            this.delegate.texture(u, v);
            this.delegate.putGlint(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            this.delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            this.delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void next() {
            this.delegate.next();
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {
            this.delegate.fixedColor(red, green, blue, alpha);
        }

        @Override
        public void unfixColor() {
            this.delegate.unfixColor();
        }
    }

    public static class GLintOverlay implements VertexConsumer {

        private final PBRVertexConsumer delegate;
        private final Matrix4f inverseTextureMatrix;
        private final Matrix3f inverseNormalMatrix;
        private final float textureScale;
        private final Vector3f normal = new Vector3f();
        private final Vector3f pos = new Vector3f();
        private final int glintTextureID;
        private float x;
        private float y;
        private float z;

        public GLintOverlay(PBRVertexConsumer delegate, RenderLayer glintRenderLayer,
            MatrixStack.Entry matrix, float textureScale) {
            this.delegate = delegate;
            if (glintRenderLayer instanceof RenderLayer.MultiPhase) {
                Identifier identifier = ((RenderLayer.MultiPhase) glintRenderLayer).phases.texture.getId()
                    .orElse(MissingSprite.getMissingSpriteId());
                this.glintTextureID = MinecraftClient.getInstance()
                    .getTextureManager()
                    .getTexture(identifier)
                    .getGlId();
            } else {
                this.glintTextureID = 0;
            }

            this.inverseTextureMatrix = new Matrix4f(matrix.getPositionMatrix()).invert();
            this.inverseNormalMatrix = new Matrix3f(matrix.getNormalMatrix()).invert();
            this.textureScale = textureScale;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.x = (float) x;
            this.y = (float) y;
            this.z = (float) z;
            this.delegate.vertex(this.x, this.y, this.z, this.glintTextureID);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            this.delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            this.delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            this.delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.delegate.normal(x, y, z);
            Vector3f transformedNormal = this.inverseNormalMatrix.transform(x, y, z, this.pos);
            Direction direction = Direction.getFacing(transformedNormal.x(), transformedNormal.y(),
                transformedNormal.z());
            Vector3f transformedPos = this.inverseTextureMatrix.transformPosition(this.x, this.y,
                this.z, this.normal);
            transformedPos.rotateY((float) Math.PI);
            transformedPos.rotateX((float) (-Math.PI / 2));
            transformedPos.rotate(direction.getRotationQuaternion());

            this.delegate.putGlint(
                -transformedPos.x() * this.textureScale,
                -transformedPos.y() * this.textureScale);
            return this;
        }

        @Override
        public void next() {
            this.delegate.next();
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {
            this.delegate.fixedColor(red, green, blue, alpha);
        }

        @Override
        public void unfixColor() {
            this.delegate.unfixColor();
        }
    }
}
