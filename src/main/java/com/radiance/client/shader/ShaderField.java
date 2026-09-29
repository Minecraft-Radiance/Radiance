package com.radiance.client.shader;

public record ShaderField(String name, String fieldName, Kind kind, int componentCount,
                          int offset, int size, int samplerSlot) {

    public enum Kind {
        INT,
        FLOAT,
        MATRIX,
        SAMPLER
    }

    public boolean isSampler() {
        return kind == Kind.SAMPLER;
    }

    public String glslType() {
        return switch (kind) {
            case INT -> switch (componentCount) {
                case 1 -> "int";
                case 2 -> "ivec2";
                case 3 -> "ivec3";
                case 4 -> "ivec4";
                default -> throw new IllegalStateException("Unsupported int vector size: " + componentCount);
            };
            case FLOAT -> switch (componentCount) {
                case 1 -> "float";
                case 2 -> "vec2";
                case 3 -> "vec3";
                case 4 -> "vec4";
                default -> throw new IllegalStateException("Unsupported float vector size: " + componentCount);
            };
            case MATRIX -> switch (componentCount) {
                case 2 -> "mat2";
                case 3 -> "mat3";
                case 4 -> "mat4";
                default -> throw new IllegalStateException("Unsupported matrix size: " + componentCount);
            };
            case SAMPLER -> "uint";
        };
    }
}
