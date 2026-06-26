package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.radiance.client.blaze3d.abi.NativeBridge;
import java.util.List;

public final class RadianceCompiledRenderPipeline implements CompiledRenderPipeline {
    private final RenderPipeline pipeline;
    private final long handle;

    RadianceCompiledRenderPipeline(RenderPipeline pipeline) {
        this.pipeline = pipeline;
        VertexFormatLayout layout = firstVertexLayout(pipeline);
        ColorTargetState[] colorTargets = pipeline.getColorTargetStates();
        GpuFormat colorFormat = colorTargets != null && colorTargets.length > 0 && colorTargets[0] != null
            ? colorTargets[0].format()
            : GpuFormat.RGBA8_UNORM;
        this.handle = NativeBridge.registerPipelineNative(
            pipeline.getLocation().toString(),
            pipeline.getVertexShader().toString(),
            pipeline.getFragmentShader().toString(),
            RadianceBackendUtil.topology(pipeline.getPrimitiveTopology()),
            RadianceBackendUtil.polygonMode(pipeline.getPolygonMode()),
            pipeline.isCull(),
            layout.slot(),
            layout.stride(),
            layout.attributeNames(),
            layout.attributeFormats(),
            layout.attributeOffsets(),
            RadianceBackendUtil.gpuFormat(colorFormat),
            pipeline.getDepthStencilState() != null);
    }

    long handle() {
        return this.handle;
    }

    RenderPipeline pipeline() {
        return this.pipeline;
    }

    @Override
    public boolean isValid() {
        return this.handle != 0L;
    }

    private static VertexFormatLayout firstVertexLayout(RenderPipeline pipeline) {
        VertexFormat[] bindings = pipeline.getVertexFormatBindings();
        if (bindings == null) {
            return VertexFormatLayout.EMPTY;
        }
        for (int slot = 0; slot < bindings.length; slot++) {
            VertexFormat format = bindings[slot];
            if (format != null) {
                List<VertexFormatElement> elements = format.getElements();
                String[] names = new String[elements.size()];
                int[] formats = new int[elements.size()];
                int[] offsets = new int[elements.size()];
                for (int i = 0; i < elements.size(); i++) {
                    VertexFormatElement element = elements.get(i);
                    names[i] = element.name();
                    formats[i] = RadianceBackendUtil.gpuFormat(element.format());
                    offsets[i] = element.offset();
                }
                return new VertexFormatLayout(slot, format.getVertexSize(), names, formats, offsets);
            }
        }
        return VertexFormatLayout.EMPTY;
    }

    private record VertexFormatLayout(int slot, int stride, String[] attributeNames, int[] attributeFormats, int[] attributeOffsets) {
        private static final VertexFormatLayout EMPTY = new VertexFormatLayout(0, 0, new String[0], new int[0], new int[0]);
    }
}
