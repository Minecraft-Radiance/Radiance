package com.radiance.loader.neoforge.scene_import.plugin;

import java.util.List;

public record Blaze3DCallsite(
    String className,
    String methodName,
    String methodDescriptor,
    int line,
    String opcode,
    String owner,
    String name,
    String descriptor,
    String category,
    List<String> nearbyStrings) {
}

