package com.radiance.client.shader;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.RadianceClient;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.ShaderProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGlUniformExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderStageExt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.ShaderInstance;

public final class ShaderRegistry {

    private static final Map<ShaderInstance, ShaderDefinition> CACHE =
        Collections.synchronizedMap(new WeakHashMap<>());

    private ShaderRegistry() {
    }

    public static ShaderDefinition getOrCreate(ShaderInstance shaderProgram) {
        ShaderDefinition cached = CACHE.get(shaderProgram);
        if (cached != null) {
            return cached;
        }

        ShaderDefinition created = create(shaderProgram);
        CACHE.put(shaderProgram, created);
        return created;
    }

    public static void clear() {
        CACHE.clear();
    }

    private static ShaderDefinition create(ShaderInstance shaderProgram) {
        IShaderProgramExt ext = (IShaderProgramExt) (Object) shaderProgram;
        List<ShaderField> fields = buildFields(ext.radiance$getUniformsValue(),
            ext.radiance$getSamplerNamesValue());

        String vertexSource =
            ((IShaderStageExt) (Object) shaderProgram.getVertexProgram()).radiance$getResolvedSource();
        String fragmentSource =
            ((IShaderStageExt) (Object) shaderProgram.getFragmentProgram()).radiance$getResolvedSource();

        VertexFormat vertexFormat = shaderProgram.getVertexFormat();
        ShaderTranslator.Result result = ShaderTranslator.translate(shaderProgram,
            vertexSource, fragmentSource, fields);

        String key = buildKey(shaderProgram.getName(), vertexFormat, result.vertexSource(),
            result.fragmentSource(), fields);
        Path directory = getShaderDirectory();
        Path vertexPath = directory.resolve(key + ".vert");
        Path fragmentPath = directory.resolve(key + ".frag");
        writeIfChanged(vertexPath, result.vertexSource());
        writeIfChanged(fragmentPath, result.fragmentSource());

        int nativeId = ShaderProxy.registerShader(key,
            Constants.VertexFormats.getValue(vertexFormat),
            Constants.DrawModes.QUADS.getValue(),
            result.uniformBufferSize(),
            vertexPath.toString(),
            fragmentPath.toString(),
            new String[0],
            new String[0]);
        return new ShaderDefinition(key, shaderProgram.getName(), nativeId,
            result.uniformBufferSize(), fields);
    }

    private static List<ShaderField> buildFields(List<Uniform> uniforms,
        List<String> samplerNames) {
        ArrayList<ShaderField> fields = new ArrayList<>();
        int offset = 0;

        for (Uniform uniform : uniforms) {
            IGlUniformExt ext = (IGlUniformExt) (Object) uniform;
            int dataType = ext.radiance$getDataTypeValue();
            int componentCount = getComponentCount(dataType);
            ShaderField.Kind kind = getKind(dataType);
            int alignment = getAlignment(kind, componentCount);
            int size = getSize(kind, componentCount);
            offset = align(offset, alignment);
            fields.add(new ShaderField(uniform.getName(), uniform.getName(), kind,
                componentCount, offset, size, -1));
            offset += size;
        }

        for (int i = 0; i < samplerNames.size(); i++) {
            String samplerName = samplerNames.get(i);
            offset = align(offset, Integer.BYTES);
            fields.add(new ShaderField(samplerName, samplerName + "Index",
                ShaderField.Kind.SAMPLER, 1, offset, Integer.BYTES, i));
            offset += Integer.BYTES;
        }

        return List.copyOf(fields);
    }

    private static int getComponentCount(int dataType) {
        return switch (dataType) {
            case 0, 4 -> 1;
            case 1, 5 -> 2;
            case 2, 6 -> 3;
            case 3, 7 -> 4;
            case 8 -> 2;
            case 9 -> 3;
            case 10 -> 4;
            default -> throw new IllegalArgumentException("Unsupported uniform type: " + dataType);
        };
    }

    private static ShaderField.Kind getKind(int dataType) {
        if (dataType <= 3) {
            return ShaderField.Kind.INT;
        }
        if (dataType <= 7) {
            return ShaderField.Kind.FLOAT;
        }
        return ShaderField.Kind.MATRIX;
    }

    private static int getAlignment(ShaderField.Kind kind, int componentCount) {
        return switch (kind) {
            case SAMPLER -> Integer.BYTES;
            case INT, FLOAT -> switch (componentCount) {
                case 1 -> Integer.BYTES;
                case 2 -> Integer.BYTES * 2;
                case 3, 4 -> Integer.BYTES * 4;
                default -> throw new IllegalStateException("Unsupported component count: " + componentCount);
            };
            case MATRIX -> Integer.BYTES * 4;
        };
    }

    private static int getSize(ShaderField.Kind kind, int componentCount) {
        return switch (kind) {
            case SAMPLER -> Integer.BYTES;
            case INT, FLOAT -> switch (componentCount) {
                case 1 -> Integer.BYTES;
                case 2 -> Integer.BYTES * 2;
                case 3, 4 -> Integer.BYTES * 4;
                default -> throw new IllegalStateException("Unsupported component count: " + componentCount);
            };
            case MATRIX -> Integer.BYTES * 4 * componentCount;
        };
    }

    private static int align(int value, int alignment) {
        return Math.ceilDiv(value, alignment) * alignment;
    }

    private static String buildKey(String shaderName, VertexFormat vertexFormat, String vertexSource,
        String fragmentSource, List<ShaderField> fields) {
        StringBuilder builder = new StringBuilder(shaderName).append('\n')
            .append(vertexFormat.toString())
            .append('\n')
            .append(vertexSource)
            .append('\n')
            .append(fragmentSource)
            .append('\n');
        for (ShaderField field : fields) {
            builder.append(field.name())
                .append(':')
                .append(field.kind())
                .append(':')
                .append(field.componentCount())
                .append(':')
                .append(field.offset())
                .append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(builder.toString()
                .getBytes(StandardCharsets.UTF_8));
            return shaderName.replaceAll("[^a-zA-Z0-9._-]", "_")
                + "-"
                + HexFormat.of()
                .formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path getShaderDirectory() {
        Path directory = RadianceClient.radianceDir.resolve("temp")
            .resolve("shaders")
            .resolve("overlay");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create shader directory", e);
        }
        return directory;
    }

    private static void writeIfChanged(Path path, String content) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(path)) {
                String existing = Files.readString(path);
                if (existing.equals(content)) {
                    return;
                }
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write shader file: " + path, e);
        }
    }
}
