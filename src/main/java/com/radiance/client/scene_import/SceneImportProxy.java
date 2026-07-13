package com.radiance.client.scene_import;

import java.nio.ByteBuffer;

public final class SceneImportProxy {
    private SceneImportProxy() {
    }

    public static native long reserveTaskIdNative();

    public static native long markSourceVersionNative(int sourceKind, long sourceValue, long version);

    public static native long deleteSourceNative(int sourceKind, long sourceValue);

    public static native long clearSourceKindNative(int sourceKind);

    public static native long updateTemporalStateNative(int sourceKind, long sourceValue,
        long generation, long serial, double positionX, double positionY, double positionZ,
        float rotationX, float rotationY, float rotationZ, float rotationW,
        float scaleX, float scaleY, float scaleZ,
        double velocityX, double velocityY, double velocityZ,
        double accelerationX, double accelerationY, double accelerationZ,
        double angularVelocityX, double angularVelocityY, double angularVelocityZ,
        int model, int capabilities, int flags, long minPredictionNs, long maxPredictionNs,
        long correctionHalfLifeNs, long maxCorrectionNs, float maxLinearSpeed,
        float maxLinearAcceleration, float maxAngularSpeed, float snapDistance,
        float snapAngleRadians);

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

    public static native long submitPbrGeometryBatchNative(int sourceKind, long sourceValue,
        long version, int domain, double originX, double originY, double originZ,
        int rayTracingMask, int coordinate, int stableHistoryId, boolean important,
        int primitiveMode, int[] geometryTypes, int[] geometryTextureIds, String[] groupNames,
        String[] contentNames, int rasterMetadataVersion, int[] rasterMetadata,
        int rasterMetadataIntStride, int sceneTagVersion, int[] sceneTags,
        int sceneTagIntStride, int[] vertexCounts, int[] vertexOffsets, ByteBuffer pbrVertices,
        int pbrVertexStride);

    public static native int registerPbrMaterialNative(String spriteId, String albedoPath,
        String normalPath, String specularPath, String flagPath);

    public static native void clearPbrMaterialsNative();

    public static native void clearAtlasNative(long atlasTexture);

    public static native void syncAtlasSpriteNative(long atlasTexture, long atlasTextureView,
        String atlasId, String spriteId, float u0, float v0, float u1, float v1, int pixelX,
        int pixelY, int pixelWidth, int pixelHeight, int materialId);

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
