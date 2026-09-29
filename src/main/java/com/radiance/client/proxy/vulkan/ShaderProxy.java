package com.radiance.client.proxy.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.shader.ShaderDefinition;
import com.radiance.client.shader.ShaderField;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGlUniformExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.Window;
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

    public static UniformHandle createUniform(ShaderDefinition shader, ShaderProgram shaderProgram,
        MemoryStack stack) {
        ByteBuffer bb = stack.calloc(shader.uniformBufferSize());
        IShaderProgramExt ext = (IShaderProgramExt) (Object) shaderProgram;
        int uniformIndex = 0;
        for (ShaderField field : shader.fields()) {
            if (field.isSampler()) {
                bb.putInt(field.offset(), resolveSamplerTextureId(ext, field));
                continue;
            }
            GlUniform uniform = ext.radiance$getUniformsValue()
                .get(uniformIndex++);
            putUniform(bb, field, uniform);
        }
        return new UniformHandle(MemoryUtil.memAddress(bb), shader.uniformBufferSize());
    }

    public static void syncState(ShaderProgram shaderProgram, VertexFormat.DrawMode drawMode) {
        for (int i = 0; i < 12; i++) {
            shaderProgram.addSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }

        if (shaderProgram.modelViewMat != null) {
            shaderProgram.modelViewMat.set(RenderSystem.getModelViewMatrix());
        }

        if (shaderProgram.projectionMat != null) {
            shaderProgram.projectionMat.set(RenderSystem.getProjectionMatrix());
        }

        if (shaderProgram.viewRotationMat != null) {
            Matrix3f inverseViewRotationMatrix = RenderSystem.getInverseViewRotationMatrix();
            shaderProgram.viewRotationMat.set(inverseViewRotationMatrix);
        }

        if (shaderProgram.colorModulator != null) {
            shaderProgram.colorModulator.set(RenderSystem.getShaderColor());
        }

        if (shaderProgram.glintAlpha != null) {
            shaderProgram.glintAlpha.set(RenderSystem.getShaderGlintAlpha());
        }

        if (shaderProgram.fogStart != null) {
            shaderProgram.fogStart.set(RenderSystem.getShaderFogStart());
        }

        if (shaderProgram.fogEnd != null) {
            shaderProgram.fogEnd.set(RenderSystem.getShaderFogEnd());
        }

        if (shaderProgram.fogColor != null) {
            shaderProgram.fogColor.set(RenderSystem.getShaderFogColor());
        }

        if (shaderProgram.fogShape != null) {
            shaderProgram.fogShape.set(RenderSystem.getShaderFogShape()
                .getId());
        }

        if (shaderProgram.textureMat != null) {
            shaderProgram.textureMat.set(RenderSystem.getTextureMatrix());
        }

        if (shaderProgram.gameTime != null) {
            shaderProgram.gameTime.set(RenderSystem.getShaderGameTime());
        }

        if (shaderProgram.screenSize != null) {
            Window window = MinecraftClient.getInstance()
                .getWindow();
            shaderProgram.screenSize.set((float) window.getFramebufferWidth(),
                (float) window.getFramebufferHeight());
        }

        if (shaderProgram.lineWidth != null
            && (drawMode == VertexFormat.DrawMode.LINES
            || drawMode == VertexFormat.DrawMode.LINE_STRIP)) {
            shaderProgram.lineWidth.set(RenderSystem.getShaderLineWidth());
        }

        RenderSystem.setupShaderLights(shaderProgram);
    }

    public record UniformHandle(long addr, int size) {

    }

    private static int resolveSamplerTextureId(IShaderProgramExt ext, ShaderField field) {
        Object sampler = ext.radiance$getSamplersValue()
            .get(field.name());
        if (sampler instanceof Framebuffer framebuffer) {
            return framebuffer.getColorAttachment();
        }
        if (sampler instanceof AbstractTexture texture) {
            return texture.getGlId();
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

    private static void putUniform(ByteBuffer bb, ShaderField field, GlUniform uniform) {
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
