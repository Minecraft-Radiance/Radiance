package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.DeviceFeatures;
import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.DeviceLimits;
import com.mojang.blaze3d.systems.DeviceType;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.systems.HintsAndWorkarounds;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.client.blaze3d.abi.NativeBridge;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class RadianceGpuDeviceBackend implements GpuDeviceBackend {
    private final Map<RenderPipeline, RadianceCompiledRenderPipeline> pipelineCache = new IdentityHashMap<>();
    private final RadianceCommandEncoderBackend commandEncoder = new RadianceCommandEncoderBackend(this);
    private final DeviceInfo deviceInfo;
    private boolean closed;

    RadianceGpuDeviceBackend() {
        int maxTextureSize = Math.max(4096, NativeBridge.maxSupportedTextureSizeNative());
        this.deviceInfo = new DeviceInfo(
            "Radiance Blaze3D Scene Capture",
            "Radiance",
            "native core",
            true,
            "Radiance Blaze3D",
            1.0F,
            new DeviceLimits(16, 1, maxTextureSize, Long.MAX_VALUE, 65535, 8),
            new DeviceFeatures(true, true, true, true, true, true, false),
            Set.of("radiance_blaze3d_scene_capture"),
            new HintsAndWorkarounds(false, false),
            DeviceType.OTHER);
    }

    @Override
    public GpuSurfaceBackend createSurface(long windowHandle) {
        return new RadianceGpuSurfaceBackend(windowHandle);
    }

    @Override
    public CommandEncoderBackend createCommandEncoder() {
        return this.commandEncoder;
    }

    @Override
    public GpuSampler createSampler(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter,
                                    FilterMode magFilter, int maxAnisotropy, OptionalDouble maxLod) {
        return new RadianceGpuSampler(addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod);
    }

    @Override
    public GpuTexture createTexture(@Nullable Supplier<String> label, @GpuTexture.Usage int usage, GpuFormat format,
                                    int width, int height, int depthOrLayers, int mipLevels) {
        return this.createTexture(label == null ? "" : label.get(), usage, format, width, height, depthOrLayers, mipLevels);
    }

    @Override
    public GpuTexture createTexture(@Nullable String label, @GpuTexture.Usage int usage, GpuFormat format,
                                    int width, int height, int depthOrLayers, int mipLevels) {
        return new RadianceGpuTexture(usage, label, format, width, height, depthOrLayers, mipLevels);
    }

    @Override
    public GpuTextureView createTextureView(GpuTexture texture) {
        return this.createTextureView(texture, 0, texture.getMipLevels());
    }

    @Override
    public GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevels) {
        return new RadianceGpuTextureView(texture, baseMipLevel, mipLevels);
    }

    @Override
    public GpuBuffer createBuffer(@Nullable Supplier<String> label, @GpuBuffer.Usage int usage, long size) {
        return this.createBuffer(label == null ? "" : label.get(), usage, size);
    }

    public GpuBuffer createBuffer(@Nullable String label, @GpuBuffer.Usage int usage, long size) {
        return new RadianceGpuBuffer(label, usage, size);
    }

    @Override
    public GpuBuffer createBuffer(@Nullable Supplier<String> label, @GpuBuffer.Usage int usage, ByteBuffer data) {
        return this.createBuffer(label == null ? "" : label.get(), usage | GpuBuffer.USAGE_COPY_DST, data);
    }

    public GpuBuffer createBuffer(@Nullable String label, @GpuBuffer.Usage int usage, ByteBuffer data) {
        ByteBuffer direct = data.isDirect() ? data.duplicate() : RadianceBackendUtil.directCopy(data);
        return new RadianceGpuBuffer(label, usage, direct);
    }

    @Override
    public List<String> getLastDebugMessages() {
        return List.of();
    }

    @Override
    public boolean isDebuggingEnabled() {
        return false;
    }

    @Override
    public CompiledRenderPipeline precompilePipeline(RenderPipeline pipeline, @Nullable ShaderSource shaderSource) {
        return this.compilePipeline(pipeline);
    }

    RadianceCompiledRenderPipeline compilePipeline(RenderPipeline pipeline) {
        return this.pipelineCache.computeIfAbsent(pipeline, RadianceCompiledRenderPipeline::new);
    }

    @Override
    public void clearPipelineCache() {
        this.pipelineCache.clear();
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            this.clearPipelineCache();
            NativeBridge.closeNative();
        }
    }

    @Override
    public GpuQueryPool createTimestampQueryPool(int size) {
        return new RadianceGpuQueryPool(size);
    }

    @Override
    public long getTimestampNow() {
        return System.nanoTime();
    }

    @Override
    public DeviceInfo getDeviceInfo() {
        return this.deviceInfo;
    }
}
