package com.radiance.client.blaze3d.scene;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.compat.TextureCompat;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.MoonPhase;

public final class SceneTextureIds {
    public static final int MISSING_TEXTURE_ID = -1;

    private static final Identifier SUN_RESOURCE =
        Identifier.withDefaultNamespace("textures/environment/celestial/sun.png");

    private static final Map<Identifier, Integer> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<Identifier, Integer> ATLAS_SPRITES = new ConcurrentHashMap<>();
    private static int sunTextureId = MISSING_TEXTURE_ID;
    private static int moonTextureId = MISSING_TEXTURE_ID;
    private static boolean skyCompatibilityAttempted;

    private SceneTextureIds() {
    }

    public static void registerTexture(Identifier resourceId, int textureId) {
        register(TEXTURES, resourceId, textureId);
    }

    public static void unregisterTexture(Identifier resourceId) {
        if (resourceId != null) {
            TEXTURES.remove(resourceId);
        }
    }

    public static int textureId(Identifier resourceId) {
        return lookup(TEXTURES, resourceId);
    }

    public static void registerAtlasSprite(Identifier spriteId, int atlasTextureId) {
        register(ATLAS_SPRITES, spriteId, atlasTextureId);
    }

    public static void clearAtlasSprites(int atlasTextureId) {
        ATLAS_SPRITES.entrySet().removeIf(entry -> entry.getValue() == atlasTextureId);
    }

    public static int atlasSpriteTextureId(Identifier spriteId) {
        return lookup(ATLAS_SPRITES, spriteId);
    }

    public static synchronized void ensureSkyCompatibilityTextures(ResourceManager resourceManager) {
        if (skyCompatibilityAttempted || resourceManager == null) {
            return;
        }
        skyCompatibilityAttempted = true;

        sunTextureId = uploadResource(resourceManager, SUN_RESOURCE);
        if (sunTextureId == MISSING_TEXTURE_ID) {
            sunTextureId = uploadTransparentImage(1, 1);
        }

        moonTextureId = uploadMoonAtlas(resourceManager);
        if (moonTextureId == MISSING_TEXTURE_ID) {
            moonTextureId = uploadTransparentImage(4, 2);
        }
    }

    public static int sunTextureId() {
        return sunTextureId;
    }

    public static int moonTextureId() {
        return moonTextureId;
    }

    private static void register(Map<Identifier, Integer> target, Identifier id, int textureId) {
        if (id == null || textureId == 0) {
            return;
        }
        target.put(id, textureId);
    }

    private static int lookup(Map<Identifier, Integer> source, Identifier id) {
        if (id == null) {
            return MISSING_TEXTURE_ID;
        }
        return source.getOrDefault(id, MISSING_TEXTURE_ID);
    }

    private static int uploadResource(ResourceManager resourceManager, Identifier id) {
        Optional<Resource> resource = resourceManager.getResource(id);
        if (resource.isEmpty()) {
            return MISSING_TEXTURE_ID;
        }

        try (InputStream stream = resource.get().open();
             NativeImage image = NativeImage.read(stream)) {
            return uploadImage(image);
        } catch (IOException ignored) {
            return MISSING_TEXTURE_ID;
        }
    }

    private static int uploadMoonAtlas(ResourceManager resourceManager) {
        NativeImage[] phases = new NativeImage[MoonPhase.COUNT];
        try {
            int tileWidth = 0;
            int tileHeight = 0;
            for (MoonPhase phase : MoonPhase.values()) {
                Identifier id = Identifier.withDefaultNamespace(
                    "textures/environment/celestial/moon/" + phase.getSerializedName() + ".png");
                Optional<Resource> resource = resourceManager.getResource(id);
                if (resource.isEmpty()) {
                    return MISSING_TEXTURE_ID;
                }
                try (InputStream stream = resource.get().open()) {
                    NativeImage image = NativeImage.read(stream);
                    phases[phase.index()] = image;
                    tileWidth = Math.max(tileWidth, image.getWidth());
                    tileHeight = Math.max(tileHeight, image.getHeight());
                }
            }

            try (NativeImage atlas = new NativeImage(NativeImage.Format.RGBA, tileWidth * 4,
                tileHeight * 2, false)) {
                for (MoonPhase phase : MoonPhase.values()) {
                    NativeImage image = phases[phase.index()];
                    int tile = phase.index();
                    int dstX = tile % 4 * tileWidth;
                    int dstY = tile / 4 * tileHeight;
                    copyImage(image, atlas, dstX, dstY);
                }
                return uploadImage(atlas);
            }
        } catch (IOException ignored) {
            return MISSING_TEXTURE_ID;
        } finally {
            for (NativeImage image : phases) {
                if (image != null) {
                    image.close();
                }
            }
        }
    }

    private static void copyImage(NativeImage source, NativeImage target, int dstX, int dstY) {
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                target.setPixel(dstX + x, dstY + y, source.getPixel(x, y));
            }
        }
    }

    private static int uploadImage(NativeImage image) {
        int textureId = TextureProxy.generateTextureId();
        TextureProxy.prepareImage(textureId, 1, image.getWidth(), image.getHeight(),
            TextureCompat.vkFormat(image.format()));
        TextureTracker.GLID2Texture.put(textureId,
            new TextureTracker.Texture(image.getWidth(), image.getHeight(), image.format(), 1));
        TextureProxy.queueUpload(TextureCompat.pointer(image), TextureCompat.byteSize(image),
            image.getWidth(), textureId, 0, 0, 0, 0, image.getWidth(), image.getHeight(), 0);
        return textureId;
    }

    private static int uploadTransparentImage(int width, int height) {
        try (NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, true)) {
            return uploadImage(image);
        }
    }
}
