package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.ColorTargetState;
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
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class RadianceGpuDeviceBackend implements GpuDeviceBackend {
    private final DeviceInfo deviceInfo;
    private boolean closed;

    RadianceGpuDeviceBackend() {
        int maxTextureSize = Math.max(4096, Blaze3DProxy.maxSupportedTextureSizeNative());
        this.deviceInfo = new DeviceInfo("Radiance Blaze3D", "Radiance", "native core", true,
            "Radiance Blaze3D", 1.0F, new DeviceLimits(16, 1, maxTextureSize, Long.MAX_VALUE, 65535, 8),
            new DeviceFeatures(true, true, true, true, true, true, false), Set.of("radiance_blaze3d"),
            new HintsAndWorkarounds(false, false), DeviceType.OTHER);
    }

    @Override
    public GpuSurfaceBackend createSurface(long windowHandle) {
        return new RadianceGpuSurfaceBackend(windowHandle, Blaze3DProxy.createSurfaceNative(windowHandle));
    }

    @Override
    public CommandEncoderBackend createCommandEncoder() {
        return new RadianceCommandEncoderBackend(Blaze3DProxy.createCommandEncoderNative());
    }

    @Override
    public GpuSampler createSampler(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter,
                                    FilterMode magFilter, int maxAnisotropy, OptionalDouble maxLod) {
        long handle = Blaze3DProxy.createSamplerNative(RadianceBackendUtil.addressMode(addressModeU),
            RadianceBackendUtil.addressMode(addressModeV), RadianceBackendUtil.filterMode(minFilter),
            RadianceBackendUtil.filterMode(magFilter), maxAnisotropy, maxLod.isPresent(), maxLod.orElse(0.0));
        return new RadianceGpuSampler(handle, addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod);
    }

    @Override
    public GpuTexture createTexture(@Nullable Supplier<String> label, @GpuTexture.Usage int usage, GpuFormat format,
                                    int width, int height, int depthOrLayers, int mipLevels) {
        return this.createTexture(label == null ? "" : label.get(), usage, format, width, height, depthOrLayers, mipLevels);
    }

    @Override
    public GpuTexture createTexture(@Nullable String label, @GpuTexture.Usage int usage, GpuFormat format,
                                    int width, int height, int depthOrLayers, int mipLevels) {
        long handle = Blaze3DProxy.createTextureNative(RadianceBackendUtil.label(label), usage,
            RadianceBackendUtil.gpuFormat(format), width, height, depthOrLayers, mipLevels);
        return new RadianceGpuTexture(handle, usage, RadianceBackendUtil.label(label), format, width, height, depthOrLayers, mipLevels);
    }

    @Override
    public GpuTextureView createTextureView(GpuTexture texture) {
        return this.createTextureView(texture, 0, texture.getMipLevels());
    }

    @Override
    public GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevels) {
        long handle = Blaze3DProxy.createTextureViewNative(RadianceBackendUtil.textureHandle(texture), baseMipLevel, mipLevels);
        return new RadianceGpuTextureView(handle, texture, baseMipLevel, mipLevels);
    }

    @Override
    public GpuBuffer createBuffer(@Nullable Supplier<String> label, @GpuBuffer.Usage int usage, long size) {
        return this.createBuffer(label == null ? "" : label.get(), usage, size);
    }

    public GpuBuffer createBuffer(@Nullable String label, @GpuBuffer.Usage int usage, long size) {
        return new RadianceGpuBuffer(Blaze3DProxy.createBufferNative(RadianceBackendUtil.label(label), usage, size), usage, size);
    }

    @Override
    public GpuBuffer createBuffer(@Nullable Supplier<String> label, @GpuBuffer.Usage int usage, ByteBuffer data) {
        return this.createBuffer(label == null ? "" : label.get(), usage, data);
    }

    public GpuBuffer createBuffer(@Nullable String label, @GpuBuffer.Usage int usage, ByteBuffer data) {
        ByteBuffer bytes = data.duplicate();
        return new RadianceGpuBuffer(Blaze3DProxy.createBufferWithDataNative(RadianceBackendUtil.label(label), usage,
            bytes, bytes.position(), bytes.remaining()), usage, bytes.remaining());
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
        VertexFormat[] bindings = pipeline.getVertexFormatBindings();
        int slot = 0;
        int stride = 0;
        String[] names = new String[0];
        int[] formats = new int[0];
        int[] offsets = new int[0];
        if (bindings != null) {
            for (int i = 0; i < bindings.length; i++) {
                VertexFormat format = bindings[i];
                if (format != null) {
                    slot = i;
                    stride = format.getVertexSize();
                    List<VertexFormatElement> elements = format.getElements();
                    names = new String[elements.size()];
                    formats = new int[elements.size()];
                    offsets = new int[elements.size()];
                    for (int e = 0; e < elements.size(); e++) {
                        VertexFormatElement element = elements.get(e);
                        names[e] = element.name();
                        formats[e] = RadianceBackendUtil.gpuFormat(element.format());
                        offsets[e] = element.offset();
                    }
                    break;
                }
            }
        }
        ColorTargetState[] colorTargets = pipeline.getColorTargetStates();
        GpuFormat colorFormat = colorTargets != null && colorTargets.length > 0 && colorTargets[0] != null ? colorTargets[0].format() : GpuFormat.RGBA8_UNORM;
        long handle = Blaze3DProxy.registerPipelineNative(pipeline.getLocation().toString(), pipeline.getVertexShader().toString(),
            pipeline.getFragmentShader().toString(), RadianceBackendUtil.topology(pipeline.getPrimitiveTopology()),
            RadianceBackendUtil.polygonMode(pipeline.getPolygonMode()), pipeline.isCull(), slot, stride, names, formats, offsets,
            RadianceBackendUtil.gpuFormat(colorFormat), pipeline.getDepthStencilState() != null);
        return new RadianceCompiledRenderPipeline(handle);
    }

    @Override
    public void clearPipelineCache() {
        Blaze3DProxy.clearPipelineCacheNative();
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            Blaze3DProxy.closeNative();
        }
    }

    @Override
    public GpuQueryPool createTimestampQueryPool(int size) {
        return new RadianceGpuQueryPool(Blaze3DProxy.createTimestampQueryPoolNative(size), size);
    }

    @Override
    public long getTimestampNow() {
        return Blaze3DProxy.getTimestampNowNative();
    }

    @Override
    public DeviceInfo getDeviceInfo() {
        return this.deviceInfo;
    }
}
