package com.radiance.client.vertex;

import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_ALBEDO_EMISSION;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_TEXTURE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_LIGHT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_OVERLAY_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POST_BASE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_ID;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_GLINT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_LIGHT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_OVERLAY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_TEXTURE;

import java.nio.ByteOrder;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.util.BufferAllocator;
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
    private static final int ALPHA_MODE_OPAQUE = 0;
    private static final int ALPHA_MODE_CUTOUT = 1;
    private static final int ALPHA_MODE_TRANSPARENT = 2;
    private static final int ALPHA_MODE_MASK = 0xF;
    public static final int MATERIAL_FLAG_RAIN_EXPOSED = 1 << 17;
    public static final int MATERIAL_FLAG_RAIN_PRECIPITATION = 1 << 18;
    public static final int MATERIAL_FLAG_RAIN_SPLASH = 1 << 19;
    public static final int MATERIAL_FLAG_WATER = 1 << 20;
    private static final int MATERIAL_FLAG_MASK =
        MATERIAL_FLAG_RAIN_EXPOSED | MATERIAL_FLAG_RAIN_PRECIPITATION | MATERIAL_FLAG_RAIN_SPLASH | MATERIAL_FLAG_WATER;
    private static final int POST_TEXT_MODE_BACKGROUND = 1;
    private static final int POST_TEXT_MODE_INTENSITY = 2;
    private static final int POST_TEXT_MODE_RGBA = 3;
    private static final int POST_TEXT_MODE_BACKGROUND_SEE_THROUGH = 4;
    private static final int POST_TEXT_MODE_INTENSITY_SEE_THROUGH = 5;
    private static final int POST_TEXT_MODE_RGBA_SEE_THROUGH = 6;
    private static final int POST_TEXT_MODE_INTENSITY_POLYGON_OFFSET = 7;
    private static final int POST_TEXT_MODE_RGBA_POLYGON_OFFSET = 8;

    private final BufferAllocator allocator;
    private final VertexFormat format;
    private final VertexFormat.DrawMode drawMode;

    private final int vertexSizeByte;
    private final int writableMask;
    private final int requiredMask;
    private final int[] offsetsByElementId;
    private final float albedoEmission = 0;
    private long vertexPointer = -1L;
    private int vertexCount = 0;
    private int currentMask = 0;
    private boolean building = true;
    private int textureID;
    private final int alphaMode;
    private final int baseMaterialFlags;
    private int materialFlags;
    private float baseX = 0;
    private float baseY = 0;
    private float baseZ = 0;

    public PBRVertexConsumer(BufferAllocator allocator, RenderLayer renderLayer) {
        this(allocator, VertexFormat.DrawMode.QUADS, PBRVertexFormats.PBR_TRIANGLE, renderLayer);
    }

    private PBRVertexConsumer(BufferAllocator allocator, VertexFormat.DrawMode drawMode,
        VertexFormat format, RenderLayer renderLayer) {
        this.allocator = allocator;
        this.drawMode = drawMode;
        this.format = format;

        this.vertexSizeByte = format.getVertexSizeByte();
        this.writableMask = format.getRequiredMask() & ~PBR_POS.getBit();
        this.requiredMask = 0;
        this.offsetsByElementId = format.getOffsetsByElementId();

        if (this.vertexSizeByte != 128) {
            throw new IllegalStateException(
                "PBR vertex stride must be 128, got " + this.vertexSizeByte);
        }
        if (!format.has(PBR_POS)) {
            throw new IllegalArgumentException("PBR format must contain POSITION element");
        }

        Identifier textureIdentifier = MissingSprite.getMissingSpriteId();
        if (renderLayer instanceof RenderLayer.MultiPhase) {
            Identifier
                identifier =
                ((RenderLayer.MultiPhase) renderLayer).phases.texture.getId()
                    .orElse(MissingSprite.getMissingSpriteId());
            textureIdentifier = identifier;
            textureID =
                MinecraftClient.getInstance()
                    .getTextureManager()
                    .getTexture(identifier)
                    .getGlId();
        }
        this.alphaMode = getAlphaMode(renderLayer, textureIdentifier);
        this.baseMaterialFlags = getBaseMaterialFlags(textureIdentifier);
        this.materialFlags = 0;
    }

    private static void putInt(long ptr, int v) {
        if (LITTLE_ENDIAN) {
            MemoryUtil.memPutInt(ptr, v);
        } else {
            MemoryUtil.memPutShort(ptr, (short) (v & 0xFFFF));
            MemoryUtil.memPutShort(ptr + 2L, (short) ((v >>> 16) & 0xFFFF));
        }
    }

    private static int getAlphaMode(RenderLayer renderLayer, Identifier textureIdentifier) {
        if (isRainAnisotropicTexture(textureIdentifier)) {
            return ALPHA_MODE_TRANSPARENT;
        }

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

        if (RenderPhase.NO_TRANSPARENCY.equals(multiPhase.phases.transparency)) {
            return ALPHA_MODE_CUTOUT;
        }

        return ALPHA_MODE_TRANSPARENT;
    }

    private static int getBaseMaterialFlags(Identifier textureIdentifier) {
        if (isVanillaTexture(textureIdentifier, "textures/environment/rain.png")) {
            return MATERIAL_FLAG_RAIN_PRECIPITATION;
        }
        if (isRainSplashTexture(textureIdentifier)) {
            return MATERIAL_FLAG_RAIN_SPLASH;
        }
        return 0;
    }

    private static boolean isRainAnisotropicTexture(Identifier textureIdentifier) {
        return isVanillaTexture(textureIdentifier, "textures/environment/rain.png")
            || isRainSplashTexture(textureIdentifier);
    }

    private static boolean isRainSplashTexture(Identifier textureIdentifier) {
        return isVanillaTexture(textureIdentifier, "textures/particle/splash_0.png")
            || isVanillaTexture(textureIdentifier, "textures/particle/splash_1.png")
            || isVanillaTexture(textureIdentifier, "textures/particle/splash_2.png")
            || isVanillaTexture(textureIdentifier, "textures/particle/splash_3.png");
    }

    private static boolean isVanillaTexture(Identifier textureIdentifier, String path) {
        return "minecraft".equals(textureIdentifier.getNamespace())
            && path.equals(textureIdentifier.getPath());
    }

    private int getEncodedAlphaMode() {
        return (this.alphaMode & ALPHA_MODE_MASK) | this.baseMaterialFlags | this.materialFlags;
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

    public void setMaterialFlags(int materialFlags) {
        this.materialFlags = materialFlags & MATERIAL_FLAG_MASK;
    }

    private void ensureBuilding() {
        if (!building) {
            throw new IllegalStateException("Not building!");
        }
    }

    @Nullable
    public BuiltBuffer endNullable() {
        ensureBuilding();
        endVertex();
        BuiltBuffer built = build();
        building = false;
        vertexPointer = -1L;
        return built;
    }

    public BuiltBuffer end() {
        BuiltBuffer built = endNullable();
        if (built == null) {
            throw new IllegalStateException("PBRBufferBuilder was empty");
        }
        return built;
    }

    @Nullable
    private BuiltBuffer build() {
        if (vertexCount == 0) {
            return null;
        }

        BufferAllocator.CloseableBuffer buf = allocator.getAllocated();
        if (buf == null) {
            return null;
        }

        int indexCount = drawMode.getIndexCount(vertexCount);
        VertexFormat.IndexType indexType = VertexFormat.IndexType.smallestFor(vertexCount);
        return new BuiltBuffer(buf,
            new BuiltBuffer.DrawParameters(format, vertexCount, indexCount, drawMode, indexType));
    }

    private long beginVertex() {
        ensureBuilding();
        endVertex();

        vertexCount++;
        long ptr = allocator.allocate(vertexSizeByte);
        vertexPointer = ptr;
        MemoryUtil.memSet(ptr, 0, vertexSizeByte);

        if (this.textureID != 0) {
            int off = this.offsetsByElementId[PBR_TEXTURE_ID.id()];
            if (off >= 0) {
                putInt(ptr + off, this.textureID);
            }
        }

        int offBase = this.offsetsByElementId[PBR_POST_BASE.id()];
        if (offBase >= 0) {
            MemoryUtil.memPutFloat(ptr + offBase, baseX);
            MemoryUtil.memPutFloat(ptr + offBase + 4L, baseY);
            MemoryUtil.memPutFloat(ptr + offBase + 8L, baseZ);
            // Reuse the trailing padding word after postBase for alpha mode and material flags.
            putInt(ptr + offBase + 12L, this.getEncodedAlphaMode());
        }

        return ptr;
    }

    private long beginVertex(int glintTextureID) {
        ensureBuilding();
        endVertex();

        vertexCount++;
        long ptr = allocator.allocate(vertexSizeByte);
        vertexPointer = ptr;
        MemoryUtil.memSet(ptr, 0, vertexSizeByte);

        if (this.textureID != 0) {
            int off = this.offsetsByElementId[PBR_TEXTURE_ID.id()];
            if (off >= 0) {
                putInt(ptr + off, this.textureID);
            }
        }

        int offBase = this.offsetsByElementId[PBR_POST_BASE.id()];
        if (offBase >= 0) {
            MemoryUtil.memPutFloat(ptr + offBase, baseX);
            MemoryUtil.memPutFloat(ptr + offBase + 4L, baseY);
            MemoryUtil.memPutFloat(ptr + offBase + 8L, baseZ);
            // Reuse the trailing padding word after postBase for alpha mode and material flags.
            putInt(ptr + offBase + 12L, this.getEncodedAlphaMode());
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
        int bit = element.getBit();
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

    private void endVertex() {
        if (vertexCount == 0) {
            return;
        }

        int missing = currentMask & requiredMask;
        if (missing != 0) {
            String
                s =
                VertexFormatElement.streamFromMask(currentMask)
                    .map(format::getName)
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Missing elements in vertex: " + s);
        }
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
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
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        long f = beginElement(PBR_USE_COLOR_LAYER);
        if (f != -1L) {
            putInt(f, 1);
        }

        long p = beginElement(PBR_COLOR_LAYER);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, red / 255.0f);
            MemoryUtil.memPutFloat(p + 4L, green / 255.0f);
            MemoryUtil.memPutFloat(p + 8L, blue / 255.0f);
            MemoryUtil.memPutFloat(p + 12L, alpha / 255.0f);
        }
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        long f = beginElement(PBR_USE_TEXTURE);
        if (f != -1L) {
            putInt(f, 1);
        }

        long p = beginElement(PBR_TEXTURE_UV);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, u);
            MemoryUtil.memPutFloat(p + 4L, v);
        }
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        long f = beginElement(PBR_USE_OVERLAY);
        if (f != -1L) {
            putInt(f, 1);
        }

        long p = beginElement(PBR_OVERLAY_UV);
        if (p != -1L) {
            putInt(p, u);
            putInt(p + 4L, v);
        }
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        long f = beginElement(PBR_USE_LIGHT);
        if (f != -1L) {
            putInt(f, 1);
        }

        long p = beginElement(PBR_LIGHT_UV);
        if (p != -1L) {
            putInt(p, u);
            putInt(p + 4L, v);
        }
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        long f = beginElement(PBR_USE_NORM);
        if (f != -1L) {
            putInt(f, 1);
        }

        long p = beginElement(PBR_NORM);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4L, y);
            MemoryUtil.memPutFloat(p + 8L, z);
        }
        return this;
    }

    public VertexConsumer albedoEmission(float emission) {
        long p = beginElement(PBR_ALBEDO_EMISSION);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, emission);
        }
        return this;
    }

    public static class GLint implements VertexConsumer {

        private final PBRVertexConsumer delegate;
        private int glintTextureID;

        public GLint(PBRVertexConsumer delegate, RenderLayer glintRenderLayer) {
            this.delegate = delegate;
            if (glintRenderLayer instanceof RenderLayer.MultiPhase) {
                Identifier
                    identifier =
                    ((RenderLayer.MultiPhase) glintRenderLayer).phases.texture.getId()
                        .orElse(MissingSprite.getMissingSpriteId());
                glintTextureID =
                    MinecraftClient.getInstance()
                        .getTextureManager()
                        .getTexture(identifier)
                        .getGlId();
            }
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z, this.glintTextureID);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);

            long f = delegate.beginElement(PBR_USE_GLINT);
            if (f != -1L) {
                putInt(f, 1);
            }

            long p = delegate.beginElement(PBR_GLINT_UV);
            if (p != -1L) {
                MemoryUtil.memPutFloat(p, u);
                MemoryUtil.memPutFloat(p + 4L, v);
            }
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
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

        public GLintOverlay(PBRVertexConsumer delegate, RenderLayer glintRenderLayer,
            MatrixStack.Entry matrix, float textureScale) {
            this.delegate = delegate;
            if (glintRenderLayer instanceof RenderLayer.MultiPhase) {
                Identifier
                    identifier =
                    ((RenderLayer.MultiPhase) glintRenderLayer).phases.texture.getId()
                        .orElse(MissingSprite.getMissingSpriteId());
                glintTextureID =
                    MinecraftClient.getInstance()
                        .getTextureManager()
                        .getTexture(identifier)
                        .getGlId();
            }

            this.inverseTextureMatrix = new Matrix4f(matrix.getPositionMatrix()).invert();
            this.inverseNormalMatrix = new Matrix3f(matrix.getNormalMatrix()).invert();
            this.textureScale = textureScale;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            delegate.vertex(x, y, z, this.glintTextureID);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            Vector3f vector3f = this.inverseNormalMatrix.transform(x, y, z, this.pos);
            Direction direction = Direction.getFacing(vector3f.x(), vector3f.y(), vector3f.z());
            Vector3f vector3f2 = this.inverseTextureMatrix.transformPosition(this.x, this.y, this.z,
                this.normal);
            vector3f2.rotateY((float) Math.PI);
            vector3f2.rotateX((float) (-Math.PI / 2));
            vector3f2.rotate(direction.getRotationQuaternion());

            long f = delegate.beginElement(PBR_USE_GLINT);
            if (f != -1L) {
                putInt(f, 1);
            }

            long p = delegate.beginElement(PBR_GLINT_UV);
            if (p != -1L) {
                MemoryUtil.memPutFloat(p, -vector3f2.x() * this.textureScale);
                MemoryUtil.memPutFloat(p + 4L, -vector3f2.y() * this.textureScale);
            }
            return this;
        }
    }
}
