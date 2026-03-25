package com.radiance.client.vertex;

import java.nio.ByteOrder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import com.radiance.mixins.vulkan_render_integration.BufferBuilderMixins;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class PBRVertexConsumer implements VertexConsumer {

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final int ALPHA_MODE_OPAQUE = 0;
    private static final int ALPHA_MODE_CUTOUT = 1;
    private static final int ALPHA_MODE_TRANSPARENT = 2;

    private final BufferBuilder bufferBuilder;
    private final VertexFormat format;
    private final VertexFormat.DrawMode drawMode;
    private int vertexCount = 0;
    private boolean building = true;
    private int textureID;
    private final int alphaMode;
    private float baseX = 0;
    private float baseY = 0;
    private float baseZ = 0;
    private boolean hasPendingVertex;
    private float pendingX;
    private float pendingY;
    private float pendingZ;
    private boolean useNorm;
    private float normX;
    private float normY;
    private float normZ;
    private boolean useColorLayer;
    private float colorR;
    private float colorG;
    private float colorB;
    private float colorA;
    private boolean useTexture;
    private float textureU;
    private float textureV;
    private boolean useOverlay;
    private int overlayU;
    private int overlayV;
    private boolean useGlint;
    private float glintU;
    private float glintV;
    private int glintTextureID;
    private boolean useLight;
    private int lightU;
    private int lightV;
    private float albedoEmission;

    public PBRVertexConsumer(int initialSize, RenderLayer renderLayer) {
        this(VertexFormat.DrawMode.QUADS, PBRVertexFormats.PBR_TRIANGLE,
            Math.max(initialSize, renderLayer.getExpectedBufferSize()), renderLayer);
    }

    public PBRVertexConsumer(RenderLayer renderLayer) {
        this(renderLayer.getExpectedBufferSize(), renderLayer);
    }

    private PBRVertexConsumer(VertexFormat.DrawMode drawMode, VertexFormat format, int initialSize,
        RenderLayer renderLayer) {
        this.bufferBuilder = new BufferBuilder(initialSize);
        this.drawMode = drawMode;
        this.format = format;
        this.bufferBuilder.begin(drawMode, format);

        if (renderLayer instanceof RenderLayer.MultiPhase) {
            Identifier
                identifier =
                ((RenderLayer.MultiPhase) renderLayer).phases.texture.getId()
                    .orElse(MissingSprite.getMissingSpriteId());
            textureID =
                MinecraftClient.getInstance()
                    .getTextureManager()
                    .getTexture(identifier)
                    .getGlId();
        }
        this.alphaMode = getAlphaMode(renderLayer);
    }

    private static void putInt(long ptr, int v) {
        throw new UnsupportedOperationException("Pointer writes are not used on 1.20.1");
    }

    private static int getAlphaMode(RenderLayer renderLayer) {
        if (!(renderLayer instanceof RenderLayer.MultiPhase multiPhase)) {
            return ALPHA_MODE_OPAQUE;
        }

        if (multiPhase.name.contains("solid")) {
            return ALPHA_MODE_OPAQUE;
        }

        if (multiPhase.name.contains("cutout")) {
            return ALPHA_MODE_CUTOUT;
        }

        return multiPhase.name.contains("translucent") || multiPhase.name.contains("glint")
            || multiPhase.name.contains("lightning") ? ALPHA_MODE_TRANSPARENT :
            ALPHA_MODE_CUTOUT;
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

    private void ensureBuilding() {
        if (!building) {
            throw new IllegalStateException("Not building!");
        }
    }

    @Nullable
    public BufferBuilder.BuiltBuffer endNullable() {
        ensureBuilding();
        flushPendingVertex();
        BufferBuilder.BuiltBuffer built = this.vertexCount == 0 ? null : this.bufferBuilder.endNullable();
        building = false;
        return built;
    }

    public BufferBuilder.BuiltBuffer end() {
        BufferBuilder.BuiltBuffer built = endNullable();
        if (built == null) {
            throw new IllegalStateException("PBRBufferBuilder was empty");
        }
        return built;
    }

    public void close() {
        if (this.building) {
            this.bufferBuilder.clear();
            this.building = false;
        }
    }

    private void writeInt(int offset, int value) {
        if (LITTLE_ENDIAN) {
            this.bufferBuilder.putByte(offset, (byte) (value & 0xFF));
            this.bufferBuilder.putByte(offset + 1, (byte) ((value >>> 8) & 0xFF));
            this.bufferBuilder.putByte(offset + 2, (byte) ((value >>> 16) & 0xFF));
            this.bufferBuilder.putByte(offset + 3, (byte) ((value >>> 24) & 0xFF));
        } else {
            this.bufferBuilder.putByte(offset, (byte) ((value >>> 24) & 0xFF));
            this.bufferBuilder.putByte(offset + 1, (byte) ((value >>> 16) & 0xFF));
            this.bufferBuilder.putByte(offset + 2, (byte) ((value >>> 8) & 0xFF));
            this.bufferBuilder.putByte(offset + 3, (byte) (value & 0xFF));
        }
    }

    private void writeFlag(boolean enabled) {
        writeInt(0, enabled ? 1 : 0);
        this.bufferBuilder.nextElement();
    }

    private void flushPendingVertex() {
        if (!this.hasPendingVertex) {
            return;
        }

        ensureBuilding();
        ((BufferBuilderMixins) (Object) this.bufferBuilder).radiance$grow(this.format.getVertexSizeByte());

        this.bufferBuilder.putFloat(0, pendingX);
        this.bufferBuilder.putFloat(4, pendingY);
        this.bufferBuilder.putFloat(8, pendingZ);
        this.bufferBuilder.nextElement();

        writeFlag(this.useNorm);
        this.bufferBuilder.putFloat(0, this.normX);
        this.bufferBuilder.putFloat(4, this.normY);
        this.bufferBuilder.putFloat(8, this.normZ);
        this.bufferBuilder.nextElement();

        writeFlag(this.useColorLayer);
        this.bufferBuilder.putFloat(0, this.colorR);
        this.bufferBuilder.putFloat(4, this.colorG);
        this.bufferBuilder.putFloat(8, this.colorB);
        this.bufferBuilder.putFloat(12, this.colorA);
        this.bufferBuilder.nextElement();

        writeFlag(this.useTexture);
        writeFlag(this.useOverlay);
        this.bufferBuilder.putFloat(0, this.textureU);
        this.bufferBuilder.putFloat(4, this.textureV);
        this.bufferBuilder.nextElement();

        writeInt(0, this.overlayU);
        writeInt(4, this.overlayV);
        this.bufferBuilder.nextElement();

        writeFlag(this.useGlint);
        writeInt(0, this.textureID);
        this.bufferBuilder.nextElement();

        this.bufferBuilder.putFloat(0, this.glintU);
        this.bufferBuilder.putFloat(4, this.glintV);
        this.bufferBuilder.nextElement();

        writeInt(0, this.glintTextureID);
        this.bufferBuilder.nextElement();
        writeFlag(this.useLight);
        writeInt(0, this.lightU);
        writeInt(4, this.lightV);
        this.bufferBuilder.nextElement();

        writeInt(0, 0);
        this.bufferBuilder.nextElement();
        this.bufferBuilder.putFloat(0, this.albedoEmission);
        this.bufferBuilder.nextElement();

        this.bufferBuilder.putFloat(0, this.baseX);
        this.bufferBuilder.putFloat(4, this.baseY);
        this.bufferBuilder.putFloat(8, this.baseZ);
        writeInt(12, this.alphaMode);
        this.bufferBuilder.nextElement();
        this.bufferBuilder.next();

        this.vertexCount++;
        this.hasPendingVertex = false;
    }

    private void beginVertex(float x, float y, float z, int glintTextureID) {
        flushPendingVertex();
        ensureBuilding();
        this.hasPendingVertex = true;
        this.pendingX = Float.isNaN(x) ? 0 : x;
        this.pendingY = Float.isNaN(y) ? 0 : y;
        this.pendingZ = Float.isNaN(z) ? 0 : z;
        this.useNorm = false;
        this.normX = 0;
        this.normY = 0;
        this.normZ = 0;
        this.useColorLayer = false;
        this.colorR = 0;
        this.colorG = 0;
        this.colorB = 0;
        this.colorA = 0;
        this.useTexture = false;
        this.textureU = 0;
        this.textureV = 0;
        this.useOverlay = false;
        this.overlayU = 0;
        this.overlayV = 0;
        this.useGlint = false;
        this.glintU = 0;
        this.glintV = 0;
        this.glintTextureID = glintTextureID;
        this.useLight = false;
        this.lightU = 0;
        this.lightV = 0;
        this.albedoEmission = 0;
    }

    void useGlint(float u, float v) {
        this.useGlint = true;
        this.glintU = u;
        this.glintV = v;
    }

    void useGlintTexture(int glintTextureID) {
        this.glintTextureID = glintTextureID;
        this.useGlint = glintTextureID != 0;
    }

    private void ensureVertexStarted() {
        if (!this.hasPendingVertex) {
            throw new IllegalStateException("Not currently building vertex");
        }
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        beginVertex((float) x, (float) y, (float) z, 0);
        return this;
    }

    public VertexConsumer vertex(float x, float y, float z) {
        beginVertex(x, y, z, 0);
        return this;
    }

    public VertexConsumer vertex(float x, float y, float z, int glintTextureID) {
        beginVertex(x, y, z, glintTextureID);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        ensureVertexStarted();
        this.useColorLayer = true;
        this.colorR = red / 255.0f;
        this.colorG = green / 255.0f;
        this.colorB = blue / 255.0f;
        this.colorA = alpha / 255.0f;
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        ensureVertexStarted();
        this.useTexture = true;
        this.textureU = u;
        this.textureV = v;
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        ensureVertexStarted();
        this.useOverlay = true;
        this.overlayU = u;
        this.overlayV = v;
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        ensureVertexStarted();
        this.useLight = true;
        this.lightU = u;
        this.lightV = v;
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        ensureVertexStarted();
        this.useNorm = true;
        this.normX = x;
        this.normY = y;
        this.normZ = z;
        return this;
    }

    public VertexConsumer albedoEmission(float emission) {
        ensureVertexStarted();
        this.albedoEmission = emission;
        return this;
    }

    @Override
    public void next() {
        flushPendingVertex();
    }

    @Override
    public void fixedColor(int red, int green, int blue, int alpha) {
        this.color(red, green, blue, alpha);
    }

    @Override
    public void unfixColor() {
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
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.useGlintTexture(this.glintTextureID);
            delegate.vertex((float) x, (float) y, (float) z, this.glintTextureID);
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
            delegate.useGlint(u, v);
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

        @Override
        public void next() {
            delegate.next();
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {
            delegate.fixedColor(red, green, blue, alpha);
        }

        @Override
        public void unfixColor() {
            delegate.unfixColor();
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
        public VertexConsumer vertex(double x, double y, double z) {
            this.x = (float) x;
            this.y = (float) y;
            this.z = (float) z;
            delegate.useGlintTexture(this.glintTextureID);
            delegate.vertex(this.x, this.y, this.z, this.glintTextureID);
            return this;
        }

        public VertexConsumer vertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            delegate.useGlintTexture(this.glintTextureID);
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
            delegate.useGlint(-vector3f2.x() * this.textureScale,
                -vector3f2.y() * this.textureScale);
            return this;
        }

        @Override
        public void next() {
            delegate.next();
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {
            delegate.fixedColor(red, green, blue, alpha);
        }

        @Override
        public void unfixColor() {
            delegate.unfixColor();
        }
    }
}
