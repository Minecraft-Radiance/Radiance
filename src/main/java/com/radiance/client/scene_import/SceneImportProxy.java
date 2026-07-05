package com.radiance.client.scene_import;

import java.nio.ByteBuffer;

public final class SceneImportProxy {
    private SceneImportProxy() {
    }

    public static native long reserveTaskIdNative();

    public static native long markSourceVersionNative(int sourceKind, long sourceValue, long version);

    public static native void configureWorkBudgetNative(int maxBuildTasks,
        int maxImportantBuildTasks, int maxNormalBuildTasks, int maxBackgroundBuildTasks,
        int maxBlasBatches, int maxImportantBlasBatches, int maxNormalBlasBatches,
        int maxBackgroundBlasBatches, boolean adaptive);

    public static native long submitComputeTaskNative(long taskId, int buildStepKind,
        int priority, String label, int kernelId, int dispatchMode, int elementCount,
        int groupCountX, int groupCountY, int groupCountZ, ByteBuffer pushConstants,
        int pushConstantsOffset, int pushConstantsSize, int[] bindingSets,
        int[] bindingIndices, int[] bindingArrayElements, int[] bindingDescriptorTypes,
        long[] bindingBuffers, long[] bindingOffsets, long[] bindingRanges, int[] bindingUsages,
        long[] bindingImages, int[] bindingImageLayouts, int[] bindingImageViewIndices,
        long[] bindingSamplers, long[] bindingTlasHandles, long[] bindingTexelBuffers,
        int[] bindingTexelFormats, long[] dependencies, int[] expectedSourceKinds,
        long[] expectedSourceValues, long[] expectedSourceVersions);

    public static native long submitGeometryBatchNative(long taskId, int buildStepKind,
        int priority, String label, int kernelId, int dispatchMode, int elementCount,
        int groupCountX, int groupCountY, int groupCountZ, ByteBuffer pushConstants,
        int pushConstantsOffset, int pushConstantsSize, int[] bindingSets,
        int[] bindingIndices, int[] bindingArrayElements, int[] bindingDescriptorTypes,
        long[] bindingBuffers, long[] bindingOffsets, long[] bindingRanges, int[] bindingUsages,
        long[] bindingImages, int[] bindingImageLayouts, int[] bindingImageViewIndices,
        long[] bindingSamplers, long[] bindingTlasHandles, long[] bindingTexelBuffers,
        int[] bindingTexelFormats, long[] dependencies, int[] expectedSourceKinds,
        long[] expectedSourceValues, long[] expectedSourceVersions, ByteBuffer geometryRecords,
        int geometryRecordOffset, int geometryRecordStride, int geometryRecordCount,
        String[] groupNames, String[] contentNames);

    public static native long submitBuildPlanNative(long planId, int sourceKind,
        long sourceValue, long sourceVersion, long[] nodeIds, int[] nodeKinds,
        int[] nodePriorities, String[] nodeLabels, int[] nodeKernels,
        int[] nodeDispatchModes, int[] nodeElementCounts, int[] nodeGroupCountX,
        int[] nodeGroupCountY, int[] nodeGroupCountZ, ByteBuffer pushConstants,
        int[] pushConstantOffsets, int[] pushConstantSizes, int[] bindingNodeIndices,
        int[] bindingSets, int[] bindingIndices, int[] bindingArrayElements,
        int[] bindingDescriptorTypes, long[] bindingBuffers, long[] bindingOffsets,
        long[] bindingRanges, int[] bindingUsages, long[] bindingImages,
        int[] bindingImageLayouts, int[] bindingImageViewIndices, long[] bindingSamplers,
        long[] bindingTlasHandles, long[] bindingTexelBuffers, int[] bindingTexelFormats,
        int[] dependencyNodeIndices, long[] dependencies, int[] expectedNodeIndices,
        int[] expectedSourceKinds, long[] expectedSourceValues, long[] expectedSourceVersions,
        int[] inputNodeIndices, int[] inputResourceKinds, int[] inputSourceKinds,
        long[] inputSourceValues, long[] inputVersions, long[] inputValues,
        int[] outputNodeIndices, int[] outputResourceKinds, int[] outputSourceKinds,
        long[] outputSourceValues, long[] outputVersions, long[] outputValues);

    public static native long[] diagnosticsNative();
}
