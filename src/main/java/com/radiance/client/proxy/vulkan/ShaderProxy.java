package com.radiance.client.proxy.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.shader.ShaderDefinition;
import com.radiance.client.shader.ShaderField;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGlUniformExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.joml.Matrix3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class ShaderProxy {

    private ShaderProxy() {
    }

    public static native int registerShader(String shaderKey, int vertexFormatType,
        int drawMode, int uniformSize, String vertexShaderPath, String fragmentShaderPath,
        String[] defineNames, String[] defineValues);

    public static native void draw(int vertexId, int indexId, int shaderId, int indexCount,
        int indexType, long uniformPtr, int uniformSize);

    public static void draw(BufferProxy.VertexIndexBufferHandle handle, int shaderId, int indexCount,
        int indexType, long uniformPtr, int uniformSize) {
        draw(handle.vertexId, handle.indexId, shaderId, indexCount, indexType, uniformPtr,
            uniformSize);
    }

    public static UniformHandle createUniform(ShaderDefinition shader, ShaderInstance shaderProgram,
        MemoryStack stack) {
        ByteBuffer bb = stack.calloc(shader.uniformBufferSize());
        IShaderProgramExt ext = (IShaderProgramExt) (Object) shaderProgram;
        int uniformIndex = 0;
        for (ShaderField field : shader.fields()) {
            if (field.isSampler()) {
                bb.putInt(field.offset(), resolveSamplerTextureId(ext, field));
                continue;
            }
            Uniform uniform = ext.radiance$getUniformsValue()
                .get(uniformIndex++);
            putUniform(bb, field, uniform);
        }
        return new UniformHandle(MemoryUtil.memAddress(bb), shader.uniformBufferSize());
    }

    public static void syncState(ShaderInstance shaderProgram, VertexFormat.Mode drawMode) {
        for (int i = 0; i < 12; i++) {
            shaderProgram.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }

        if (shaderProgram.MODEL_VIEW_MATRIX != null) {
            shaderProgram.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        }

        if (shaderProgram.PROJECTION_MATRIX != null) {
            shaderProgram.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }

        if (shaderProgram.INVERSE_VIEW_ROTATION_MATRIX != null) {
            Matrix3f inverseViewRotationMatrix = RenderSystem.getInverseViewRotationMatrix();
            shaderProgram.INVERSE_VIEW_ROTATION_MATRIX.set(inverseViewRotationMatrix);
        }

        if (shaderProgram.COLOR_MODULATOR != null) {
            shaderProgram.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }

        if (shaderProgram.GLINT_ALPHA != null) {
            shaderProgram.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
        }

        if (shaderProgram.FOG_START != null) {
            shaderProgram.FOG_START.set(RenderSystem.getShaderFogStart());
        }

        if (shaderProgram.FOG_END != null) {
            shaderProgram.FOG_END.set(RenderSystem.getShaderFogEnd());
        }

        if (shaderProgram.FOG_COLOR != null) {
            shaderProgram.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }

        if (shaderProgram.FOG_SHAPE != null) {
            shaderProgram.FOG_SHAPE.set(RenderSystem.getShaderFogShape()
                .getIndex());
        }

        if (shaderProgram.TEXTURE_MATRIX != null) {
            shaderProgram.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }

        if (shaderProgram.GAME_TIME != null) {
            shaderProgram.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }

        if (shaderProgram.SCREEN_SIZE != null) {
            Window window = Minecraft.getInstance()
                .getWindow();
            shaderProgram.SCREEN_SIZE.set((float) window.getWidth(),
                (float) window.getHeight());
        }

        if (shaderProgram.LINE_WIDTH != null
            && (drawMode == VertexFormat.Mode.LINES
            || drawMode == VertexFormat.Mode.LINE_STRIP)) {
            shaderProgram.LINE_WIDTH.set(RenderSystem.getShaderLineWidth());
        }

        RenderSystem.setupShaderLights(shaderProgram);
    }

    public record UniformHandle(long addr, int size) {

    }

    private static int resolveSamplerTextureId(IShaderProgramExt ext, ShaderField field) {
        Object sampler = ext.radiance$getSamplersValue()
            .get(field.name());
        if (sampler instanceof RenderTarget framebuffer) {
            return framebuffer.getColorTextureId();
        }
        if (sampler instanceof AbstractTexture texture) {
            return texture.getId();
        }
        if (sampler instanceof Integer textureId) {
            return textureId;
        }
        Integer fallbackSlot = tryParseSamplerSlot(field.name());
        if (fallbackSlot != null) {
            return RenderSystem.getShaderTexture(fallbackSlot);
        }
        return field.samplerSlot() >= 0 ? RenderSystem.getShaderTexture(field.samplerSlot()) : 0;
    }

    private static Integer tryParseSamplerSlot(String samplerName) {
        if (!samplerName.startsWith("Sampler")) {
            return null;
        }
        try {
            return Integer.parseInt(samplerName.substring("Sampler".length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void putUniform(ByteBuffer bb, ShaderField field, Uniform uniform) {
        IGlUniformExt ext = (IGlUniformExt) (Object) uniform;
        switch (field.kind()) {
            case INT -> putInts(bb, field.offset(), ext.radiance$getIntDataValue(), field.componentCount());
            case FLOAT -> putFloats(bb, field.offset(), ext.radiance$getFloatDataValue(),
                field.componentCount());
            case MATRIX -> putMatrix(bb, field.offset(), field.componentCount(), uniform.getName(),
                ext.radiance$getFloatDataValue());
            case SAMPLER -> throw new IllegalStateException("Sampler fields are written separately");
        }
    }

    private static void putInts(ByteBuffer bb, int offset, IntBuffer values, int componentCount) {
        for (int i = 0; i < componentCount; i++) {
            bb.putInt(offset + i * Integer.BYTES, values.get(i));
        }
    }

    private static void putFloats(ByteBuffer bb, int offset, FloatBuffer values, int componentCount) {
        for (int i = 0; i < componentCount; i++) {
            bb.putFloat(offset + i * Float.BYTES, values.get(i));
        }
    }

    private static void putMatrix(ByteBuffer bb, int offset, int dimension, String uniformName,
        FloatBuffer values) {
        if (dimension == 4) {
            float[] matrix = new float[16];
            for (int i = 0; i < 16; i++) {
                matrix[i] = values.get(i);
            }
            if ("ProjMat".equals(uniformName)) {
                mapProjectionMatrix(matrix);
            }
            for (int i = 0; i < 16; i++) {
                bb.putFloat(offset + i * Float.BYTES, matrix[i]);
            }
            return;
        }

        int columnStride = Float.BYTES * 4;
        for (int column = 0; column < dimension; column++) {
            for (int row = 0; row < dimension; row++) {
                bb.putFloat(offset + column * columnStride + row * Float.BYTES,
                    values.get(column * dimension + row));
            }
        }
    }

    private static void mapProjectionMatrix(float[] matrix) {
        for (int column = 0; column < 4; column++) {
            int base = column * 4;
            float row0 = matrix[base];
            float row1 = matrix[base + 1];
            float row2 = matrix[base + 2];
            float row3 = matrix[base + 3];
            matrix[base] = row0;
            matrix[base + 1] = -row1;
            matrix[base + 2] = row2 * 0.5F + row3 * 0.5F;
            matrix[base + 3] = row3;
        }
    }
}
