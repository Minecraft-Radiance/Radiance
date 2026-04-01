package com.radiance.client.texture;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.util.MaterialToolkit;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public enum AuxiliaryTextures {
    SPECULAR("specular", "_s", (identifier, source) -> {
        String namespace = identifier.getNamespace();
        String path = identifier.getPath();
        String[] pathComponents = path.split("/");
        String[] fileNameComponents = pathComponents[pathComponents.length - 1].split("\\.");
        String specularFileName = String.join("",
            new String[]{fileNameComponents[0], "_s.", fileNameComponents[1]});

        pathComponents[pathComponents.length - 1] = specularFileName;
        String specularPath = String.join("/", pathComponents);
        Identifier specularIdentifier = Identifier.of(namespace, specularPath);
        return List.of(specularIdentifier);
    }, INativeImageExt::radiance$getSpecularNativeImage,
        INativeImageExt::radiance$setSpecularNativeImage,
        TextureTracker.GLID2SpecularGLID),
    NORMAL("normal", "_n", (identifier, source) -> {
        String namespace = identifier.getNamespace();
        String path = identifier.getPath();
        String[] pathComponents = path.split("/");
        String[] fileNameComponents = pathComponents[pathComponents.length - 1].split("\\.");
        String normalFileName = String.join("",
            new String[]{fileNameComponents[0], "_n.", fileNameComponents[1]});

        pathComponents[pathComponents.length - 1] = normalFileName;
        String normalPath = String.join("/", pathComponents);
        Identifier normalIdentifier = Identifier.of(namespace, normalPath);
        return List.of(normalIdentifier);
    }, INativeImageExt::radiance$getNormalNativeImage,
        INativeImageExt::radiance$setNormalNativeImage, TextureTracker.GLID2NormalGLID),
    FLAG(
        "flag", "_f", (identifier, source) -> {
        String namespace = identifier.getNamespace();
        String path = identifier.getPath();
        String[] pathComponents = path.split("/");
        String[] fileNameComponents = pathComponents[pathComponents.length - 1].split("\\.");
        String flagFileName = String.join("",
            new String[]{fileNameComponents[0], "_f.", fileNameComponents[1]});

        pathComponents[pathComponents.length - 1] = flagFileName;
        String flagPath = String.join("/", pathComponents)
            .replace("textures/", "textures/flag/");
        Identifier flagIdentifier = Identifier.of(namespace, flagPath);
        return List.of(flagIdentifier);
    }, INativeImageExt::radiance$getFlagNativeImage,
        INativeImageExt::radiance$setFlagNativeImage, TextureTracker.GLID2FlagGLID);

    private static final List<AuxiliaryTextures> ALL_TEXTURES = Collections.unmodifiableList(
        Arrays.stream(values()).collect(Collectors.toList()));
    private final String suffix;
    private final IdentifierCandidateProvider identifierCandidateProvider;
    private final Getter getter;
    private final Setter setter;
    private final String name;
    private final Map<Integer, Integer> GLIDMapping;

    AuxiliaryTextures(String name, String suffix,
        IdentifierCandidateProvider identifierCandidateProvider, Getter getter, Setter setter,
        Map<Integer, Integer> GLIDMapping) {
        this.suffix = suffix;
        this.identifierCandidateProvider = identifierCandidateProvider;
        this.getter = getter;
        this.setter = setter;
        this.name = name;
        this.GLIDMapping = GLIDMapping;
    }

    public static void loadAndUpload(NativeImage source, INativeImageExt sourceExt, int level,
        int offsetX, int offsetY, int unpackSkipPixels, int unpackSkipRows, int regionWidth,
        int regionHeight, boolean blur) {
        int targetId = sourceExt.radiance$getTargetID();
        Identifier identifier = sourceExt.radiance$getIdentifier();

        ResourceManager resourceManager = MinecraftClient.getInstance().getResourceManager();

        if (identifier != null) {
            if (ALL_TEXTURES.stream().anyMatch(texture -> {
                String path = identifier.getPath();
                int dotIndex = path.lastIndexOf('.');
                String baseName = (dotIndex != -1) ? path.substring(0, dotIndex) : path;

                return baseName.endsWith(texture.suffix);
            })) {
                return;
            }

            for (AuxiliaryTextures auxiliaryTexture : ALL_TEXTURES) {
                NativeImage auxiliaryTemplateImage = auxiliaryTexture.getter.get(sourceExt);
                int auxiliaryTargetId;

                // ensure the texture exists
                TextureTracker.Texture texture = TextureTracker.GLID2Texture.get(targetId);
                VulkanConstants.VkFormat auxiliaryFormat = texture.format().toUnorm();
                if (!auxiliaryTexture.GLIDMapping.containsKey(targetId)) {
                    auxiliaryTargetId = TextureProxy.generateTextureId();
//                    System.out.println(
//                        "generate " + auxiliaryTexture.name + " texture for " + targetId + ": "
//                            + auxiliaryTargetId);

                    TextureProxy.prepareImage(auxiliaryTargetId, texture.maxLayer() + 1,
                        texture.width(), texture.height(), auxiliaryFormat);
                    TextureTracker.GLID2Texture.put(auxiliaryTargetId,
                        new TextureTracker.Texture(texture.width(), texture.height(),
                            texture.channel(), auxiliaryFormat, texture.maxLayer()));
                    auxiliaryTexture.GLIDMapping.put(targetId, auxiliaryTargetId);
                } else {
                    auxiliaryTargetId = auxiliaryTexture.GLIDMapping.get(targetId);

                    TextureTracker.Texture auxiliaryTrackerTexture = TextureTracker.GLID2Texture.get(
                        auxiliaryTargetId);
                    if (texture.width() != auxiliaryTrackerTexture.width()
                        || texture.height() != auxiliaryTrackerTexture.height()
                        || auxiliaryTrackerTexture.format() != auxiliaryFormat) {
                        TextureProxy.prepareImage(auxiliaryTargetId, texture.maxLayer() + 1,
                            texture.width(), texture.height(), auxiliaryFormat);
                        TextureTracker.GLID2Texture.put(auxiliaryTargetId,
                            new TextureTracker.Texture(texture.width(), texture.height(),
                                texture.channel(), auxiliaryFormat, texture.maxLayer()));
                    }
                }

                if (auxiliaryTemplateImage == null && (
                    identifier.getPath().contains("textures/block") || identifier.getPath()
                        .contains("textures/item") || identifier.getPath()
                        .contains("textures/entity"))) {
                    List<Identifier> candidates = auxiliaryTexture.identifierCandidateProvider.get(
                        identifier, source);

                    boolean success = false;
                    for (Identifier candidate : candidates) {
                        Optional<Resource> optionalResource = resourceManager.getResource(
                            candidate);
                        if (optionalResource.isPresent()) {
                            try (NativeImage tmpImage = NativeImage.read(
                                optionalResource.get().getInputStream())) {
                                auxiliaryTemplateImage = MipmapUtil.getSpecificMipmapLevelImage(
                                    tmpImage, level);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

                            success = true;
                            break;
                        }
                    }

                    if (!success) {
                        auxiliaryTemplateImage = MaterialToolkit.isAutoPbrEnabled()
                            ? AutoPBRGenerator.generate(auxiliaryTexture, identifier, source)
                            : source.applyToCopy(i -> 0);
                    }
                }

                if (auxiliaryTemplateImage != null) {
                    NativeImage auxiliaryImage = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) auxiliaryTemplateImage).radiance$alignTo(
                        source);
                    ((INativeImageExt) (Object) auxiliaryImage).radiance$setTargetID(
                        auxiliaryTargetId);
                    if (auxiliaryTemplateImage != auxiliaryImage) {
                        auxiliaryTemplateImage.close();
                    }

                    if (auxiliaryImage.getWidth() != source.getWidth()
                        || auxiliaryImage.getHeight() != source.getHeight()
                        || auxiliaryImage.getFormat() != source.getFormat()) {
                        throw new RuntimeException(
                            auxiliaryTexture.name + " image size / format mismatch");
                    }

                    auxiliaryImage.upload(level, offsetX, offsetY, unpackSkipPixels, unpackSkipRows,
                        regionWidth, regionHeight, blur);
                    auxiliaryTexture.setter.set(sourceExt, auxiliaryImage);
                }
            }
        }
    }

    public interface IdentifierCandidateProvider {

        List<Identifier> get(Identifier identifier, NativeImage source);
    }

    public interface Getter {

        NativeImage get(INativeImageExt nativeImageExt);
    }

    public interface Setter {

        void set(INativeImageExt nativeImageExt, NativeImage nativeImage);
    }
}
