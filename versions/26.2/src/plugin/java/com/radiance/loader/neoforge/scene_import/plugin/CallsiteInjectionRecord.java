package com.radiance.loader.neoforge.scene_import.plugin;

import java.util.List;

public record CallsiteInjectionRecord(
    String className,
    String methodName,
    String methodDescriptor,
    int line,
    String owner,
    String name,
    String descriptor,
    String category,
    String ruleId,
    String semantic,
    String sceneRoute,
    String format,
    String importKernel,
    String rasterClass,
    String materialPolicy,
    String transform,
    int sceneRouteMask,
    int formatId,
    int importKernelId,
    int rasterClassId,
    int materialPolicyId,
    int transformRecipeId,
    boolean injected,
    List<String> nearbyStrings) {
}

