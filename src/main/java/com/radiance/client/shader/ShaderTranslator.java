package com.radiance.client.shader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.render.VertexFormat;

public final class ShaderTranslator {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\s*#version\\b.*$");
    private static final Pattern UNIFORM_PATTERN = Pattern.compile(
        "^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+[\\w\\d_]+(?:\\s*\\[[^]]*])?\\s+(\\w+)\\s*;\\s*$");
    private static final Pattern INPUT_OUTPUT_PATTERN = Pattern.compile(
        "^\\s*(in|out)\\s+([\\w\\d_]+)\\s+(\\w+)\\s*;\\s*$");
    private static final Pattern OUTPUT_ASSIGNMENT_PATTERN = Pattern.compile(
        "^(\\s*)(\\w+)\\s*=\\s*(.+);\\s*$");

    private ShaderTranslator() {
    }

    public static Result translate(VertexFormat vertexFormat, String vertexSource,
        String fragmentSource, List<ShaderField> fields) {
        Set<String> uniformNames = fields.stream()
            .filter(field -> !field.isSampler())
            .map(ShaderField::name)
            .collect(java.util.stream.Collectors.toSet());
        Set<String> samplerNames = fields.stream()
            .filter(ShaderField::isSampler)
            .map(ShaderField::name)
            .collect(java.util.stream.Collectors.toSet());

        LinkedHashMap<String, Integer> attributeLocations = new LinkedHashMap<>();
        List<String> attributeNames = vertexFormat.getAttributeNames();
        for (int i = 0; i < attributeNames.size(); i++) {
            attributeLocations.put(attributeNames.get(i), i);
        }

        StageResult vertex = rewriteStage(vertexSource, true, attributeLocations,
            new LinkedHashMap<>(), uniformNames, samplerNames);
        StageResult fragment = rewriteStage(fragmentSource, false, new LinkedHashMap<>(),
            vertex.varyingLocations(), uniformNames, samplerNames);

        int uniformBufferSize = fields.stream()
            .mapToInt(field -> field.offset() + field.size())
            .max()
            .orElse(0);
        uniformBufferSize = Math.ceilDiv(uniformBufferSize, 16) * 16;
        uniformBufferSize += 16;

        String header = buildHeader(fields);
        return new Result(header + vertex.source(), header + fragment.source(), uniformBufferSize);
    }

    private static StageResult rewriteStage(String source, boolean vertexStage,
        Map<String, Integer> inputLocations, Map<String, Integer> varyingLocations,
        Set<String> uniformNames, Set<String> samplerNames) {
        StringBuilder builder = new StringBuilder();
        LinkedHashMap<String, Integer> nextVaryingLocations = new LinkedHashMap<>(varyingLocations);
        LinkedHashMap<String, Integer> outputLocations =
            vertexStage ? nextVaryingLocations : new LinkedHashMap<>();
        int nextOutputLocation = vertexStage ? nextVaryingLocations.size() : 0;

        for (String line : source.split("\\R", -1)) {
            if (VERSION_PATTERN.matcher(line)
                .matches()) {
                continue;
            }

            if (UNIFORM_PATTERN.matcher(line)
                .matches()) {
                continue;
            }

            Matcher ioMatcher = INPUT_OUTPUT_PATTERN.matcher(line);
            if (ioMatcher.matches()) {
                String qualifier = ioMatcher.group(1);
                String type = ioMatcher.group(2);
                String name = ioMatcher.group(3);
                if ("in".equals(qualifier)) {
                    Integer location = inputLocations.get(name);
                    if (location != null) {
                        builder.append("layout(location = ")
                            .append(location)
                            .append(") in ")
                            .append(type)
                            .append(' ')
                            .append(name)
                            .append(';')
                            .append('\n');
                        continue;
                    }
                    Integer varyingLocation = varyingLocations.get(name);
                    if (varyingLocation != null) {
                        builder.append("layout(location = ")
                            .append(varyingLocation)
                            .append(") in ")
                            .append(type)
                            .append(' ')
                            .append(name)
                            .append(';')
                            .append('\n');
                        continue;
                    }
                } else if ("out".equals(qualifier)) {
                    Integer location = outputLocations.get(name);
                    if (location == null) {
                        location = nextOutputLocation++;
                        outputLocations.put(name, location);
                    }
                    builder.append("layout(location = ")
                        .append(location)
                        .append(") out ")
                        .append(type)
                        .append(' ')
                        .append(name)
                        .append(';')
                        .append('\n');
                    continue;
                }
            }

            if (!vertexStage) {
                Matcher assignmentMatcher = OUTPUT_ASSIGNMENT_PATTERN.matcher(line);
                if (assignmentMatcher.matches() && outputLocations.containsKey(assignmentMatcher.group(2))) {
                    builder.append(assignmentMatcher.group(1))
                        .append(assignmentMatcher.group(2))
                        .append(" = radianceConvertOverlaySdrToHdr((")
                        .append(assignmentMatcher.group(3))
                        .append("), radianceSdrBrightnessNits);\n");
                    continue;
                }
            }

            builder.append(line)
                .append('\n');
        }

        return new StageResult(builder.toString(), nextVaryingLocations);
    }

    private static String buildHeader(List<ShaderField> fields) {
        StringBuilder builder = new StringBuilder();
        builder.append("#version 460\n");
        builder.append("#extension GL_EXT_nonuniform_qualifier : enable\n\n");
        builder.append("layout(set = 0, binding = 0) uniform sampler2D textures[];\n");
        builder.append("layout(std140, set = 1, binding = 0) uniform Uniforms {\n");
        for (ShaderField field : fields) {
            builder.append("    ")
                .append(field.glslType())
                .append(' ')
                .append(field.fieldName())
                .append(";\n");
        }
        builder.append("    float radianceSdrBrightnessNits;\n");
        builder.append("    vec3 radianceSdrPadding;\n");
        builder.append("} uniforms;\n\n");
        for (ShaderField field : fields) {
            if (field.isSampler()) {
                builder.append("#define ")
                    .append(field.name())
                    .append(" textures[nonuniformEXT(int(uniforms.")
                    .append(field.fieldName())
                    .append("))]\n");
            } else {
                builder.append("#define ")
                    .append(field.name())
                    .append(" (uniforms.")
                    .append(field.fieldName())
                    .append(")\n");
            }
        }
        builder.append("#define radianceSdrBrightnessNits (uniforms.radianceSdrBrightnessNits)\n");
        builder.append("vec3 radianceSrgbToLinear(vec3 color) {\n");
        builder.append("    bvec3 useLinearSegment = lessThanEqual(color, vec3(0.04045));\n");
        builder.append("    vec3 linearSegment = color / 12.92;\n");
        builder.append("    vec3 exponentialSegment = pow((color + 0.055) / 1.055, vec3(2.4));\n");
        builder.append("    return mix(exponentialSegment, linearSegment, useLinearSegment);\n");
        builder.append("}\n");
        builder.append("vec4 radianceConvertOverlaySdrToHdr(vec4 color, float sdrBrightnessNits) {\n");
        builder.append("    if (sdrBrightnessNits <= 0.0) { return color; }\n");
        builder.append("    float multiplier = sdrBrightnessNits / 80.0;\n");
        builder.append("    return vec4(radianceSrgbToLinear(max(color.rgb, vec3(0.0))) * multiplier, color.a);\n");
        builder.append("}\n");
        builder.append('\n');
        return builder.toString();
    }

    public record Result(String vertexSource, String fragmentSource, int uniformBufferSize) {
    }

    private record StageResult(String source, LinkedHashMap<String, Integer> varyingLocations) {
    }
}
