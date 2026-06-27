package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;
import java.util.OptionalDouble;

public final class RadianceGpuSampler extends GpuSampler {
    private final long handle;
    private final AddressMode addressModeU;
    private final AddressMode addressModeV;
    private final FilterMode minFilter;
    private final FilterMode magFilter;
    private final int maxAnisotropy;
    private final OptionalDouble maxLod;

    RadianceGpuSampler(long handle, AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter,
                       FilterMode magFilter, int maxAnisotropy, OptionalDouble maxLod) {
        this.handle = handle;
        this.addressModeU = addressModeU;
        this.addressModeV = addressModeV;
        this.minFilter = minFilter;
        this.magFilter = magFilter;
        this.maxAnisotropy = maxAnisotropy;
        this.maxLod = maxLod;
    }

    long handle() {
        return this.handle;
    }

    @Override public AddressMode getAddressModeU() { return this.addressModeU; }
    @Override public AddressMode getAddressModeV() { return this.addressModeV; }
    @Override public FilterMode getMinFilter() { return this.minFilter; }
    @Override public FilterMode getMagFilter() { return this.magFilter; }
    @Override public int getMaxAnisotropy() { return this.maxAnisotropy; }
    @Override public OptionalDouble getMaxLod() { return this.maxLod; }

    @Override
    public void close() {
        Blaze3DProxy.closeSamplerNative(this.handle);
    }
}
