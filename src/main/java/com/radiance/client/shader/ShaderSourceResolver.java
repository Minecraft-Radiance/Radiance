package com.radiance.client.shader;

import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlImportProcessor;
import net.minecraft.client.gl.ShaderStage;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.Identifier;
import net.minecraft.util.PathUtil;
import org.apache.commons.io.IOUtils;

public final class ShaderSourceResolver {

    private ShaderSourceResolver() {
    }

    public static String resolve(ShaderStage.Type type, String name) {
        ResourceFactory factory = MinecraftClient.getInstance().getResourceManager();
        String path = "shaders/core/" + name + type.getFileExtension();
        Identifier id = Identifier.ofVanilla(path);
        try (Reader reader = factory.openAsReader(id)) {
            String source = IOUtils.toString(reader);
            GlImportProcessor importProcessor = new GlImportProcessor() {
                private final Set<String> visitedImports = new HashSet<>();

                @Override
                public String loadImport(boolean inline, String importName) {
                    String normalized = PathUtil.normalizeToPosix(
                        (inline ? PathUtil.getPosixFullPath(path) : "shaders/include/")
                            + importName);
                    if (!visitedImports.add(normalized)) {
                        return null;
                    }

                    try (Reader importReader = factory.openAsReader(Identifier.of(normalized))) {
                        return IOUtils.toString(importReader);
                    } catch (IOException e) {
                        return "#error " + e.getMessage();
                    }
                }
            };
            List<String> lines = importProcessor.readSource(source);
            return String.join("", lines);
        } catch (IOException e) {
            throw new RuntimeException("Failed to resolve shader source: " + path, e);
        }
    }
}
