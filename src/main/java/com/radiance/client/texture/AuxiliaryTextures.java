package com.radiance.client.texture;

import com.mojang.blaze3d.platform.TextureUtil;
//#if MC>=260200
//$$ import com.radiance.client.blaze3d.abi.TextureProxy;
//#else
import com.radiance.client.proxy.vulkan.TextureProxy;
//#endif
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
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.atlas.AtlasSource;
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
        INativeImageExt::radiance$setSpecularNativeImage, source -> 0,
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
        INativeImageExt::radiance$setNormalNativeImage,
        source -> source.getFormat().hasAlpha() ? 255 << source.getFormat().getAlphaOffset() : 0,
        TextureTracker.GLID2NormalGLID),
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
        INativeImageExt::radiance$setFlagNativeImage, source -> 0,
        TextureTracker.GLID2FlagGLID);

    private static final List<AuxiliaryTextures> ALL_TEXTURES = Collections.unmodifiableList(
        Arrays.stream(values()).collect(Collectors.toList()));
    private static final Object DECODED_IMAGE_CACHE_LOCK = new Object();
    private static final Map<CacheKey, CacheEntry> DECODED_IMAGE_CACHE = new ConcurrentHashMap<>();
    private final String suffix;
    private final IdentifierCandidateProvider identifierCandidateProvider;
    private final Getter getter;
    private final Setter setter;
    private final DefaultValueProvider defaultValueProvider;
    private final String name;
    private final Map<Integer, Integer> GLIDMapping;

    AuxiliaryTextures(String name, String suffix,
        IdentifierCandidateProvider identifierCandidateProvider, Getter getter, Setter setter,
        DefaultValueProvider defaultValueProvider, Map<Integer, Integer> GLIDMapping) {
        this.suffix = suffix;
        this.identifierCandidateProvider = identifierCandidateProvider;
        this.getter = getter;
        this.setter = setter;
        this.defaultValueProvider = defaultValueProvider;
        this.name = name;
        this.GLIDMapping = GLIDMapping;
    }

    public static boolean isAuxiliaryTexture(Identifier identifier) {
        if (identifier == null) {
            return false;
        }

        String path = identifier.getPath();
        int dotIndex = path.lastIndexOf('.');
        String baseName = (dotIndex != -1) ? path.substring(0, dotIndex) : path;

        return ALL_TEXTURES.stream().anyMatch(texture -> texture.matchesSuffix(baseName));
    }

    public static boolean shouldSkipAtlasSprite(ResourceManager resourceManager,
        Identifier spriteId) {
        String spritePath = spriteId.getPath();
        for (AuxiliaryTextures auxiliaryTexture : ALL_TEXTURES) {
            if (!auxiliaryTexture.matchesSuffix(spritePath)) {
                continue;
            }

            Identifier baseSpriteId = auxiliaryTexture.toBaseSpriteId(spriteId);
            if (resourceManager.getResource(
                    AtlasSource.RESOURCE_FINDER.toResourcePath(baseSpriteId))
                .isPresent()) {
                return true;
            }
        }

        return false;
    }

    public static void clearDecodedImageCache() {
        synchronized (DECODED_IMAGE_CACHE_LOCK) {
            for (CacheEntry entry : DECODED_IMAGE_CACHE.values()) {
                if (entry.levels == null) {
                    continue;
                }
                for (NativeImage level : entry.levels) {
                    if (level != null) {
                        level.close();
                    }
                }
            }
            DECODED_IMAGE_CACHE.clear();
        }
    }

    public static void loadAndUpload(NativeImage source, INativeImageExt sourceExt, int level,
        int offsetX, int offsetY, int unpackSkipPixels, int unpackSkipRows, int regionWidth,
        int regionHeight, boolean blur) {
        int targetId = sourceExt.radiance$getTargetID();
        Identifier identifier = sourceExt.radiance$getIdentifier();

        if (identifier != null) {
            if (isAuxiliaryTexture(identifier)) {
                return;
            }

            for (AuxiliaryTextures auxiliaryTexture : ALL_TEXTURES) {
                NativeImage auxiliaryTemplateImage = null;
                int auxiliaryTargetId;

                // ensure the texture exists
                TextureTracker.Texture texture = TextureTracker.GLID2Texture.get(targetId);
                if (!auxiliaryTexture.GLIDMapping.containsKey(targetId)) {
                    auxiliaryTargetId = TextureProxy.generateTextureId();
//                    System.out.println(
//                        "generate " + auxiliaryTexture.name + " texture for " + targetId + ": "
//                            + auxiliaryTargetId);

                    TextureUtil.prepareImage(texture.format().getNativeImageInternalFormat(),
                        auxiliaryTargetId, texture.maxLayer(), texture.width(), texture.height());
                    auxiliaryTexture.GLIDMapping.put(targetId, auxiliaryTargetId);
                } else {
                    auxiliaryTargetId = auxiliaryTexture.GLIDMapping.get(targetId);

                    TextureTracker.Texture auxiliaryTrackerTexture = TextureTracker.GLID2Texture.get(
                        auxiliaryTargetId);
                    if (texture.width() != auxiliaryTrackerTexture.width()
                        || texture.height() != auxiliaryTrackerTexture.height()
                        || texture.format() != auxiliaryTrackerTexture.format()) {
                        TextureUtil.prepareImage(texture.format().getNativeImageInternalFormat(),
                            auxiliaryTargetId, texture.maxLayer(), texture.width(),
                            texture.height());
                    }
                }

                if (auxiliaryTemplateImage == null && (
                    identifier.getPath().contains("textures/block") || identifier.getPath()
                        .contains("textures/item") || identifier.getPath()
                        .contains("textures/entity"))) {
                    NativeImage preparedLevelCopy = auxiliaryTexture.copyPreparedImage(identifier,
                        level);
                    if (preparedLevelCopy != null) {
                        auxiliaryTemplateImage = preparedLevelCopy;
                    } else {
                        int defaultValue = auxiliaryTexture.defaultValueProvider.get(source);
                        auxiliaryTemplateImage = source.applyToCopy(i -> defaultValue);
                    }
                }

                if (auxiliaryTemplateImage == null) {
                    continue;
                }

                NativeImage auxiliaryImage = null;
                try {
                    auxiliaryImage = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) auxiliaryTemplateImage).radiance$alignTo(
                        source);
                    if (auxiliaryTemplateImage != auxiliaryImage) {
                        auxiliaryTemplateImage.close();
                    }

                    ((INativeImageExt) (Object) auxiliaryImage).radiance$setTargetID(
                        auxiliaryTargetId);

                    if (auxiliaryImage.getWidth() != source.getWidth()
                        || auxiliaryImage.getHeight() != source.getHeight()
                        || auxiliaryImage.getFormat() != source.getFormat()) {
                        throw new RuntimeException(
                            auxiliaryTexture.name + " image size / format mismatch");
                    }

                    if (level == 0 && auxiliaryTexture == SPECULAR) {
                        long tileKey = EmissionRecorder.buildTileKey(offsetX, offsetY,
                            regionWidth, regionHeight);
                        if (TextureProxy.hasEmissionTile(targetId, tileKey)) {
                            auxiliaryImage.upload(level, offsetX, offsetY, unpackSkipPixels,
                                unpackSkipRows, regionWidth, regionHeight, blur);
                            continue;
                        }

                        TextureProxy.uploadEmissionTile(EmissionRecorder.buildTileUpdate(targetId,
                            source, auxiliaryImage, offsetX, offsetY, unpackSkipPixels,
                            unpackSkipRows, regionWidth, regionHeight));
                    }

                    auxiliaryImage.upload(level, offsetX, offsetY, unpackSkipPixels, unpackSkipRows,
                        regionWidth, regionHeight, blur);
                } finally {
                    if (auxiliaryImage != null) {
                        auxiliaryImage.close();
                    }
                }
            }
        }
    }

    private boolean matchesSuffix(String path) {
        return path.endsWith(suffix);
    }

    private Identifier toBaseSpriteId(Identifier spriteId) {
        String spritePath = spriteId.getPath();
        return spriteId.withPath(spritePath.substring(0, spritePath.length() - suffix.length()));
    }

    private CacheKey toBaseCacheKey(Identifier auxiliaryIdentifier) {
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
        return new CacheKey(this, Identifier.of(auxiliaryIdentifier.getNamespace(),
            baseName + path.substring(dotIndex)));
    }

    private CacheEntry getPreparedEntry(Identifier identifier) {
        return DECODED_IMAGE_CACHE.getOrDefault(new CacheKey(this, identifier), CacheEntry.MISSING);
    }

    private NativeImage copyPreparedImage(Identifier identifier, int level) {
        synchronized (DECODED_IMAGE_CACHE_LOCK) {
            NativeImage preparedLevel = this.getPreparedEntry(identifier).getImage(level);
            if (preparedLevel == null) {
                return null;
            }

            NativeImage copied = new NativeImage(preparedLevel.getFormat(),
                preparedLevel.getWidth(), preparedLevel.getHeight(), false);
            copied.copyFrom(preparedLevel);
            return copied;
        }
    }

    private static AuxiliaryTextures classifyAuxiliaryResource(Identifier id) {
        String path = id.getPath();
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

    public static CompletableFuture<PreparedImages> prepareDecodedImagesAsync(
        ResourceManager resourceManager, Executor prepareExecutor) {
        List<CompletableFuture<DecodedEntry>> futures = new ArrayList<>();
        Map<Identifier, Resource> resources = resourceManager.findResources("textures",
            id -> classifyAuxiliaryResource(id) != null);

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            AuxiliaryTextures auxiliaryTexture = classifyAuxiliaryResource(entry.getKey());
            if (auxiliaryTexture == null) {
                continue;
            }
            CacheKey cacheKey = auxiliaryTexture.toBaseCacheKey(entry.getKey());
            Resource resource = entry.getValue();
            futures.add(CompletableFuture.supplyAsync(
                () -> decodePreparedEntry(cacheKey, resource), prepareExecutor));
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(CompletableFuture[]::new));
        return allFutures.thenApply(unused -> {
            PreparedImages prepared = new PreparedImages();
            for (CompletableFuture<DecodedEntry> future : futures) {
                prepared.add(future.join());
            }
            return prepared;
        });
    }

    private static DecodedEntry decodePreparedEntry(CacheKey cacheKey, Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            NativeImage image = NativeImage.read(inputStream);
            NativeImage[] levels = MipmapUtil.buildMipmapChain(image);
            return new DecodedEntry(cacheKey, new CacheEntry(levels));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void applyPreparedImages(PreparedImages prepared) {
        synchronized (DECODED_IMAGE_CACHE_LOCK) {
            clearDecodedImageCache();
            DECODED_IMAGE_CACHE.putAll(prepared.entries);
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

    private record CacheKey(AuxiliaryTextures texture, Identifier identifier) {}

    private static final class CacheEntry {

        private static final CacheEntry MISSING = new CacheEntry(null);

        private final NativeImage[] levels;

        private CacheEntry(NativeImage[] levels) {
            this.levels = levels;
        }

        private NativeImage getImage(int level) {
            if (levels == null || levels.length == 0) {
                return null;
            }
            return levels[Math.min(level, levels.length - 1)];
        }
    }

    private record DecodedEntry(CacheKey cacheKey, CacheEntry cacheEntry) {}

    public static final class PreparedImages {

        private final Map<CacheKey, CacheEntry> entries = new ConcurrentHashMap<>();

        private void add(DecodedEntry entry) {
            this.entries.put(entry.cacheKey(), entry.cacheEntry());
        }

        private void close() {
            for (CacheEntry entry : this.entries.values()) {
                if (entry.levels == null) {
                    continue;
                }
                for (NativeImage level : entry.levels) {
                    if (level != null) {
                        level.close();
                    }
                }
            }
            this.entries.clear();
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

    public interface DefaultValueProvider {

        int get(NativeImage source);
    }
}
