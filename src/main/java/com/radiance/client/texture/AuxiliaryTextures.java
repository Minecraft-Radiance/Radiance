package com.radiance.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.option.Options;
import com.radiance.client.profiler.Profiler;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTracker.TextureBinding;
import com.radiance.client.texture.TextureTracker.TextureTarget;
import com.radiance.client.ui.UiBackendBuildConfig;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public enum AuxiliaryTextures {
    SPECULAR("specular", "_s", TextureProxy.UPLOAD_KIND_SPECULAR,
        new float[] {0.0f, 0.0f, 0.0f, 0.0f}, true, TextureTracker.GLID2SpecularGLID),
    NORMAL("normal", "_n", TextureProxy.UPLOAD_KIND_NORMAL,
        new float[] {0.5f, 0.5f, 1.0f, 1.0f}, true, TextureTracker.GLID2NormalGLID),
    FLAG("flag", "_f", TextureProxy.UPLOAD_KIND_FLAG,
        new float[] {0.0f, 0.0f, 0.0f, 0.0f}, false, TextureTracker.GLID2FlagGLID);

    private static final List<AuxiliaryTextures> AUXILIARY_TEXTURES = Collections.unmodifiableList(
        Arrays.stream(values()).collect(Collectors.toList()));
    private static final Object decodedImageCacheLock = new Object();
    private static final Map<BaseImageKey, DecodedImageLevels> decodedImageLevelsByKey = new ConcurrentHashMap<>();
    private static final Map<AlignedImageKey, NativeImage> alignedImagesByKey =
        new ConcurrentHashMap<>();
    private static final Map<UploadRegionKey, CachedUploadRegions> uploadRegionsByKey =
        new ConcurrentHashMap<>();
    private final String suffix;
    private final String name;
    private final int uploadKind;
    private final float[] defaultClearColor;
    private final boolean linearFilter;
    private final Map<Integer, TextureBinding> auxiliaryBindingsByBaseTextureId;
    private final Map<Integer, Integer> allocatedTextureIdsByBaseTextureId = new ConcurrentHashMap<>();
    private final Map<Integer, Long> preparedBaseGenerationsByBaseTextureId = new ConcurrentHashMap<>();

    AuxiliaryTextures(String name, String suffix, int uploadKind, float[] defaultClearColor,
        boolean linearFilter, Map<Integer, TextureBinding> auxiliaryBindingsByBaseTextureId) {
        this.suffix = suffix;
        this.name = name;
        this.uploadKind = uploadKind;
        this.defaultClearColor = defaultClearColor;
        this.linearFilter = linearFilter;
        this.auxiliaryBindingsByBaseTextureId = auxiliaryBindingsByBaseTextureId;
    }

    public static boolean isAuxiliaryTexture(ResourceLocation identifier) {
        if (identifier == null) {
            return false;
        }

        String path = identifier.getPath();
        int dotIndex = path.lastIndexOf('.');
        String baseName = (dotIndex != -1) ? path.substring(0, dotIndex) : path;

        return AUXILIARY_TEXTURES.stream().anyMatch(texture -> texture.matchesSuffix(baseName));
    }

    public static boolean shouldSkipAtlasSprite(ResourceManager resourceManager,
        ResourceLocation spriteId) {
        String spritePath = spriteId.getPath();
        for (AuxiliaryTextures auxiliaryTexture : AUXILIARY_TEXTURES) {
            if (!auxiliaryTexture.matchesSuffix(spritePath)) {
                continue;
            }

            ResourceLocation baseSpriteId = auxiliaryTexture.toBaseSpriteId(spriteId);
            if (resourceManager.getResource(
                    SpriteSource.TEXTURE_ID_CONVERTER.idToFile(baseSpriteId))
                .isPresent()) {
                return true;
            }
        }

        return false;
    }

    public static void clearDecodedImageCache() {
        synchronized (decodedImageCacheLock) {
            for (DecodedImageLevels decodedLevels : decodedImageLevelsByKey.values()) {
                if (decodedLevels.levels == null) {
                    continue;
                }
                for (NativeImage level : decodedLevels.levels) {
                    if (level != null) {
                        level.close();
                    }
                }
            }
            decodedImageLevelsByKey.clear();
            closeCachedImages(alignedImagesByKey);
            uploadRegionsByKey.clear();
            for (AuxiliaryTextures auxiliaryTexture : AUXILIARY_TEXTURES) {
                auxiliaryTexture.releaseAllocatedTextures();
            }
        }
    }

    public static List<TextureProxy.TextureUploadRegion> buildUploadRegions(NativeImage source,
        INativeImageExt sourceImageExt, int level,
        int offsetX, int offsetY, int unpackSkipPixels, int unpackSkipRows, int regionWidth,
        int regionHeight) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.AuxiliaryTextures.buildUploadRegions");
        }
        try {
            List<TextureProxy.TextureUploadRegion> uploadRegions = null;
            TextureTarget baseTarget = sourceImageExt.radiance$getTarget();
            ResourceLocation identifier = sourceImageExt.radiance$getIdentifier();

            TextureTracker.Texture texture = TextureTracker.currentTexture(baseTarget);
            if (texture == null || identifier == null || isAuxiliaryTexture(identifier)) {
                return List.of();
            }
            if (level != 0) {
                return List.of();
            }

            int baseTextureId = baseTarget.id();
            String identifierPath = identifier.getPath();
            UploadRegionKey uploadRegionKey = new UploadRegionKey(baseTextureId, identifier, level,
                offsetX, offsetY, unpackSkipPixels, unpackSkipRows, regionWidth, regionHeight,
                source.getWidth(), source.getHeight(), source.format(), texture);
            CachedUploadRegions cachedUploadRegions = uploadRegionsByKey.get(uploadRegionKey);
            if (cachedUploadRegions != null) {
                cachedUploadRegions.replayEmissionUpdate();
                return cachedUploadRegions.uploadRegions();
            }

            boolean isUploadCandidatePath = isUploadCandidatePath(identifierPath);
            EmissionRecorder.TileUpdate emissionUpdate = null;
            for (AuxiliaryTextures auxiliaryTexture : AUXILIARY_TEXTURES) {
                if (Profiler.ENABLED) {
                    Profiler.enter("java.AuxiliaryTextures.buildUploadRegions." + auxiliaryTexture.name);
                }
                try {
                    NativeImage auxiliaryImage = null;

                    if (isUploadCandidatePath) {
                        if (Profiler.ENABLED) {
                            Profiler.enter("java.AuxiliaryTextures.resolveImage." + auxiliaryTexture.name);
                        }
                        try {
                            auxiliaryImage = auxiliaryTexture.getAlignedImage(identifier, level, source);
                        } finally {
                            if (Profiler.ENABLED) {
                                Profiler.exit();
                            }
                        }
                    }

                    if (level == 0 && auxiliaryTexture == SPECULAR && Options.collectChunkEmission) {
                        long tileKey = EmissionRecorder.buildTileKey(offsetX, offsetY,
                            regionWidth, regionHeight);
                        if (!TextureProxy.hasEmissionUpdate(baseTextureId, tileKey)) {
                            if (auxiliaryImage == null) {
                                emissionUpdate = new EmissionRecorder.TileUpdate(baseTextureId, tileKey,
                                    List.of());
                            } else {
                                emissionUpdate = EmissionRecorder.buildTileUpdate(baseTextureId,
                                    source, auxiliaryImage, offsetX, offsetY, unpackSkipPixels,
                                    unpackSkipRows, regionWidth, regionHeight);
                            }
                            if (emissionUpdate != null) {
                                if (Profiler.ENABLED) {
                                    Profiler.enter("java.AuxiliaryTextures.buildEmissionUpdate");
                                }
                                try {
                                    TextureProxy.queueEmissionUpdate(emissionUpdate);
                                } finally {
                                    if (Profiler.ENABLED) {
                                        Profiler.exit();
                                    }
                                }
                            }
                        }
                    }

                    if (auxiliaryImage == null) {
                        continue;
                    }

                    if (auxiliaryImage.getWidth() != source.getWidth()
                        || auxiliaryImage.getHeight() != source.getHeight()
                        || auxiliaryImage.format() != source.format()) {
                        throw new RuntimeException(
                            auxiliaryTexture.name + " image size / format mismatch");
                    }

                    TextureTarget auxiliaryTarget = auxiliaryTexture.ensureTargetTexture(baseTarget, texture);
                    if (uploadRegions == null) {
                        uploadRegions = new ArrayList<>(AUXILIARY_TEXTURES.size());
                    }
                    uploadRegions.add(auxiliaryTexture.createUploadRegion(auxiliaryImage, auxiliaryTarget,
                        level, offsetX,
                        offsetY, unpackSkipPixels, unpackSkipRows, regionWidth, regionHeight));
                } finally {
                    if (Profiler.ENABLED) {
                        Profiler.exit();
                    }
                }
            }
            List<TextureProxy.TextureUploadRegion> uploadRegionsResult =
                uploadRegions == null ? List.of() : List.copyOf(uploadRegions);
            uploadRegionsByKey.put(uploadRegionKey, new CachedUploadRegions(uploadRegionsResult, emissionUpdate));
            return uploadRegionsResult;
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private synchronized TextureTarget ensureTargetTexture(TextureTarget baseTarget,
        TextureTracker.Texture texture) {
        int baseTextureId = baseTarget.id();
        if (texture == null) {
            throw new IllegalStateException(
                "Base texture " + baseTextureId + " was not prepared before auxiliary upload");
        }
        int auxiliaryTextureId = allocatedTextureIdsByBaseTextureId.computeIfAbsent(baseTextureId,
            unused -> allocateAuxiliaryTextureId());
        TextureTracker.Texture auxiliaryTrackerTexture = TextureTracker.GLID2Texture.get(auxiliaryTextureId);
        boolean mustPrepare = auxiliaryTrackerTexture == null
            || texture.width() != auxiliaryTrackerTexture.width()
            || texture.height() != auxiliaryTrackerTexture.height()
            || texture.format() != auxiliaryTrackerTexture.format()
            || auxiliaryTrackerTexture.maxLayer() != 0;
        boolean mustResetContents = !auxiliaryBindingsByBaseTextureId.containsKey(baseTextureId)
            || preparedBaseGenerationsByBaseTextureId.getOrDefault(baseTextureId, -1L) != texture.generation();

        if (mustPrepare) {
            TextureProxy.prepareClearedImage(auxiliaryTextureId, 1,
                texture.width(), texture.height(), texture.format(), defaultClearColor[0],
                defaultClearColor[1], defaultClearColor[2], defaultClearColor[3], texture.generation());
        } else if (mustResetContents) {
            TextureProxy.queueTextureClear(auxiliaryTextureId, defaultClearColor[0],
                defaultClearColor[1], defaultClearColor[2], defaultClearColor[3], texture.generation());
        }

        int filter = linearFilter ? VulkanConstants.VkFilter.VK_FILTER_LINEAR.getValue()
            : VulkanConstants.VkFilter.VK_FILTER_NEAREST.getValue();
        int mipmapMode = linearFilter
            ? VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR.getValue()
            : VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue();
        TextureProxy.setFilter(auxiliaryTextureId, filter, mipmapMode);
        preparedBaseGenerationsByBaseTextureId.put(baseTextureId, texture.generation());
        TextureTarget auxiliaryTarget = TextureTracker.captureTarget(auxiliaryTextureId);
        TextureTracker.bindAuxiliary(auxiliaryBindingsByBaseTextureId, baseTarget, auxiliaryTarget);
        return auxiliaryTarget;
    }

    private synchronized void releaseAllocatedTextures() {
        for (int textureId : allocatedTextureIdsByBaseTextureId.values()) {
            TextureProxy.releaseTexture(textureId);
            if (UiBackendBuildConfig.OPENGL_UI) {
                TextureUtil.releaseTextureId(textureId);
            }
        }
        auxiliaryBindingsByBaseTextureId.clear();
        allocatedTextureIdsByBaseTextureId.clear();
        preparedBaseGenerationsByBaseTextureId.clear();
    }

    private static int allocateAuxiliaryTextureId() {
        if (UiBackendBuildConfig.OPENGL_UI) {
            return TextureUtil.generateTextureId();
        }
        return TextureProxy.generateTextureId();
    }

    private TextureProxy.TextureUploadRegion createUploadRegion(NativeImage image, TextureTarget target, int level,
        int offsetX, int offsetY, int unpackSkipPixels, int unpackSkipRows, int regionWidth,
        int regionHeight) {
        long sourcePointer = ((com.radiance.mixin_related.extensions.vulkan_render_integration
            .INativeImageExt) (Object) image).radiance$getPointer();
        int sourceSizeBytes = image.getWidth() * image.getHeight() * image.format().components();
        return new TextureProxy.TextureUploadRegion(sourcePointer, sourceSizeBytes, image.getWidth(), target.id(),
            unpackSkipPixels, unpackSkipRows, offsetX, offsetY, regionWidth, regionHeight, level,
            uploadKind, image.format().components(), target.generation());
    }

    private NativeImage getAlignedImage(ResourceLocation identifier, int level, NativeImage source) {
        AlignedImageKey key = new AlignedImageKey(this, identifier, level, source.getWidth(),
            source.getHeight(), source.format());
        synchronized (decodedImageCacheLock) {
            NativeImage cached = alignedImagesByKey.get(key);
            if (cached != null) {
                return cached;
            }

            NativeImage decodedLevel = this.getDecodedImageLevels(identifier).getImage(level);
            if (decodedLevel == null) {
                return null;
            }

            NativeImage copiedImage = null;
            NativeImage alignedImage = null;
            try {
                copiedImage = new NativeImage(decodedLevel.format(), decodedLevel.getWidth(),
                    decodedLevel.getHeight(), false);
                copiedImage.copyFrom(decodedLevel);
                alignedImage = ((com.radiance.mixin_related.extensions.vulkan_render_integration
                    .INativeImageExt) (Object) copiedImage).radiance$alignTo(source);
                if (copiedImage != alignedImage) {
                    copiedImage.close();
                    copiedImage = null;
                }
                alignedImagesByKey.put(key, alignedImage);
                return alignedImage;
            } catch (RuntimeException | Error e) {
                if (alignedImage != null && alignedImage != copiedImage) {
                    alignedImage.close();
                }
                if (copiedImage != null) {
                    copiedImage.close();
                }
                throw e;
            }
        }
    }

    private static boolean isUploadCandidatePath(String path) {
        return path.contains("textures/block")
            || path.contains("textures/item")
            || path.contains("textures/entity");
    }

    private static void closeCachedImages(Map<?, NativeImage> cachedImages) {
        for (NativeImage image : cachedImages.values()) {
            image.close();
        }
        cachedImages.clear();
    }

    private boolean matchesSuffix(String path) {
        return path.endsWith(suffix);
    }

    private ResourceLocation toBaseSpriteId(ResourceLocation spriteId) {
        String spritePath = spriteId.getPath();
        return spriteId.withPath(spritePath.substring(0, spritePath.length() - suffix.length()));
    }

    private BaseImageKey toBaseImageKey(ResourceLocation auxiliaryIdentifier) {
        String path = auxiliaryIdentifier.getPath();
        if (this == FLAG) {
            path = path.replaceFirst("^textures/flag/", "textures/");
        }

        int dotIndex = path.lastIndexOf('.');
        String baseName = path.substring(0, dotIndex);
        if (!baseName.endsWith(suffix)) {
            throw new IllegalArgumentException("Unexpected auxiliary path: " + auxiliaryIdentifier);
        }
        baseName = baseName.substring(0, baseName.length() - suffix.length());
        return new BaseImageKey(this, ResourceLocation.tryBuild(auxiliaryIdentifier.getNamespace(),
            baseName + path.substring(dotIndex)));
    }

    private DecodedImageLevels getDecodedImageLevels(ResourceLocation identifier) {
        return decodedImageLevelsByKey.getOrDefault(new BaseImageKey(this, identifier), DecodedImageLevels.MISSING);
    }

    private static AuxiliaryTextures classifyAuxiliaryResource(ResourceLocation identifier) {
        String path = identifier.getPath();
        if (!path.endsWith(".png")) {
            return null;
        }
        if (isTrackedFlagPath(path) && path.endsWith(FLAG.suffix + ".png")) {
            return FLAG;
        }
        if (isTrackedTexturePath(path) && path.endsWith(SPECULAR.suffix + ".png")) {
            return SPECULAR;
        }
        if (isTrackedTexturePath(path) && path.endsWith(NORMAL.suffix + ".png")) {
            return NORMAL;
        }
        return null;
    }

    public static CompletableFuture<DecodedAuxiliaryImages> decodeImagesAsync(
        ResourceManager resourceManager, Executor decodeExecutor) {
        List<CompletableFuture<DecodedAuxiliaryImage>> decodeFutures = new ArrayList<>();
        Map<ResourceLocation, Resource> auxiliaryResources = resourceManager.listResources("textures",
            identifier -> classifyAuxiliaryResource(identifier) != null);

        for (Map.Entry<ResourceLocation, Resource> resourceEntry : auxiliaryResources.entrySet()) {
            AuxiliaryTextures auxiliaryTexture = classifyAuxiliaryResource(resourceEntry.getKey());
            if (auxiliaryTexture == null) {
                continue;
            }
            BaseImageKey imageKey = auxiliaryTexture.toBaseImageKey(resourceEntry.getKey());
            Resource resource = resourceEntry.getValue();
            decodeFutures.add(CompletableFuture.supplyAsync(
                () -> decodeAuxiliaryImage(imageKey, resource), decodeExecutor));
        }

        CompletableFuture<Void> allDecodedImages = CompletableFuture.allOf(
            decodeFutures.toArray(CompletableFuture[]::new));
        return allDecodedImages.thenApply(unused -> {
            DecodedAuxiliaryImages decodedImages = new DecodedAuxiliaryImages();
            for (CompletableFuture<DecodedAuxiliaryImage> future : decodeFutures) {
                decodedImages.add(future.join());
            }
            return decodedImages;
        });
    }

    private static DecodedAuxiliaryImage decodeAuxiliaryImage(BaseImageKey baseImageKey, Resource resource) {
        try (InputStream inputStream = resource.open()) {
            NativeImage image = NativeImage.read(inputStream);
            return new DecodedAuxiliaryImage(baseImageKey, new DecodedImageLevels(new NativeImage[] {image}));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void applyDecodedImages(DecodedAuxiliaryImages decodedImages) {
        synchronized (decodedImageCacheLock) {
            clearDecodedImageCache();
            decodedImageLevelsByKey.putAll(decodedImages.imageLevelsByKey);
        }
    }

    private static boolean isTrackedTexturePath(String path) {
        return path.startsWith("textures/block/")
            || path.startsWith("textures/item/")
            || path.startsWith("textures/entity/");
    }

    private static boolean isTrackedFlagPath(String path) {
        return path.startsWith("textures/flag/block/")
            || path.startsWith("textures/flag/item/")
            || path.startsWith("textures/flag/entity/");
    }

    private record BaseImageKey(AuxiliaryTextures auxiliaryTexture, ResourceLocation identifier) {}

    private record AlignedImageKey(AuxiliaryTextures auxiliaryTexture, ResourceLocation identifier, int level,
                                   int width, int height, NativeImage.Format format) {}

    private record UploadRegionKey(int baseTextureId,
                                   ResourceLocation identifier,
                                   int level,
                                   int offsetX,
                                   int offsetY,
                                   int unpackSkipPixels,
                                   int unpackSkipRows,
                                   int regionWidth,
                                   int regionHeight,
                                   int sourceWidth,
                                   int sourceHeight,
                                   NativeImage.Format sourceFormat,
                                   TextureTracker.Texture targetTexture) {}

    private record CachedUploadRegions(List<TextureProxy.TextureUploadRegion> uploadRegions,
                                       EmissionRecorder.TileUpdate emissionUpdate) {
        private void replayEmissionUpdate() {
            if (emissionUpdate == null
                || TextureProxy.hasEmissionUpdate(emissionUpdate.textureId, emissionUpdate.tileKey)) {
                return;
            }
            TextureProxy.queueEmissionUpdate(emissionUpdate);
        }
    }

    private static final class DecodedImageLevels {

        private static final DecodedImageLevels MISSING = new DecodedImageLevels(null);

        private final NativeImage[] levels;

        private DecodedImageLevels(NativeImage[] levels) {
            this.levels = levels;
        }

        private NativeImage getImage(int level) {
            if (levels == null || level < 0 || level >= levels.length) {
                return null;
            }
            return levels[level];
        }
    }

    private record DecodedAuxiliaryImage(BaseImageKey baseImageKey, DecodedImageLevels imageLevels) {}

    public static final class DecodedAuxiliaryImages {

        private final Map<BaseImageKey, DecodedImageLevels> imageLevelsByKey = new ConcurrentHashMap<>();

        private void add(DecodedAuxiliaryImage decodedImage) {
            this.imageLevelsByKey.put(decodedImage.baseImageKey(), decodedImage.imageLevels());
        }

    }

}
