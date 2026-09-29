package com.radiance.client.shader;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.commons.io.IOUtils;

public final class ShaderSourceResolver {

    private ShaderSourceResolver() {
    }

    public static String resolve(Program.Type type, String name) {
        ResourceProvider factory = Minecraft.getInstance().getResourceManager();
        String path = "shaders/core/" + name + type.getExtension();
        ResourceLocation id = new ResourceLocation(path);
        try (Reader reader = factory.openAsReader(id)) {
            String source = IOUtils.toString(reader);
            GlslPreprocessor importProcessor = new GlslPreprocessor() {
                private final Set<String> visitedImports = new HashSet<>();

                @Override
                public String applyImport(boolean inline, String importName) {
                    String normalized = FileUtil.normalizeResourcePath(
                        (inline ? FileUtil.getFullResourcePath(path) : "shaders/include/")
                            + importName);
                    if (!visitedImports.add(normalized)) {
                        return null;
                    }

                    try (Reader importReader = factory.openAsReader(new ResourceLocation(normalized))) {
                        return IOUtils.toString(importReader);
                    } catch (IOException e) {
                        return "#error " + e.getMessage();
                    }
                }
            };
            List<String> lines = importProcessor.process(source);
            return String.join("", lines);
        } catch (IOException e) {
            throw new RuntimeException("Failed to resolve shader source: " + path, e);
        }
    }
}
