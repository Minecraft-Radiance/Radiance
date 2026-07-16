package com.radiance.client.blaze3d.scene;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.client.blaze3d.backend.RadianceTextureHandles;
import com.radiance.client.texture.AuxiliaryTextures;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.compat.TextureCompat;
import com.radiance.client.texture.compat.TextureUploadCompat;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

public final class TextureAtlasSync {
    private TextureAtlasSync() {
    }

    public static void sync(TextureAtlas atlas, Map<Identifier, TextureAtlasSprite> sprites) {
        GpuTexture texture = safeTexture(atlas);
        GpuTextureView textureView = safeTextureView(atlas);
        if (texture == null || textureView == null) {
            return;
        }
        int atlasTextureId = RadianceTextureHandles.textureId(texture);
        if (atlasTextureId == 0) {
            return;
        }

        long atlasViewHandle = RadianceTextureHandles.textureViewHandle(textureView);
        SceneTextureIds.clearAtlasSprites(atlasTextureId);
        TextureTracker.clearAtlas(atlasTextureId);
        for (Map.Entry<Identifier, TextureAtlasSprite> entry : sprites.entrySet()) {
            TextureAtlasSprite sprite = entry.getValue();
            Identifier spriteId = sprite.contents().name();
            SceneTextureIds.registerAtlasSprite(spriteId, atlasTextureId);
            int padding = ((TextureAtlasSpriteInfo) sprite).radiance$getPadding();
            TextureTracker.syncAtlasSprite(atlasTextureId, atlasViewHandle, sprite.atlasLocation(),
                spriteId, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(),
                sprite.getX() + padding, sprite.getY() + padding, sprite.contents().width(),
                sprite.contents().height());

            uploadSpriteFrame(atlasTextureId, sprite, 0, 0, -1, -1, 0.0F);
        }
    }

    public static void syncAnimatedSprites(TextureAtlas atlas,
        List<TextureAtlasSprite> sprites,
        List<SpriteContents.AnimationState> animationStates) {
        GpuTexture texture = safeTexture(atlas);
        if (texture == null) {
            return;
        }
        int atlasTextureId = RadianceTextureHandles.textureId(texture);
        if (atlasTextureId == 0) {
            return;
        }

        int animationIndex = 0;
        for (TextureAtlasSprite sprite : sprites) {
            if (!sprite.isAnimated()) {
                continue;
            }
            if (animationIndex >= animationStates.size()) {
                return;
            }

            SpriteAnimationStateInfo animation =
                (SpriteAnimationStateInfo) animationStates.get(animationIndex);
            int nextFrameX = animation.radiance$isInterpolated()
                ? animation.radiance$getNextFrameX()
                : -1;
            int nextFrameY = animation.radiance$isInterpolated()
                ? animation.radiance$getNextFrameY()
                : -1;
            uploadSpriteFrame(atlasTextureId, sprite, animation.radiance$getCurrentFrameX(),
                animation.radiance$getCurrentFrameY(), nextFrameX, nextFrameY,
                animation.radiance$getFrameProgress());
            animationIndex++;
        }
    }

    private static GpuTexture safeTexture(TextureAtlas atlas) {
        try {
            return atlas.getTexture();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static GpuTextureView safeTextureView(TextureAtlas atlas) {
        try {
            return atlas.getTextureView();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static Identifier texturePath(Identifier spriteId) {
        return spriteId.withPath(path -> "textures/" + path + ".png");
    }

    private static void uploadSpriteFrame(int atlasTextureId, TextureAtlasSprite sprite,
        int currentFrameX, int currentFrameY, int nextFrameX, int nextFrameY,
        float frameProgress) {
        Identifier textureId = texturePath(sprite.contents().name());
        SpriteContentsImages spriteImages = (SpriteContentsImages) sprite.contents();
        com.mojang.blaze3d.platform.NativeImage[] images =
            (com.mojang.blaze3d.platform.NativeImage[]) spriteImages.radiance$getByMipLevel();
        int padding = ((TextureAtlasSpriteInfo) sprite).radiance$getPadding();

        for (int level = 0; level < images.length; level++) {
            com.mojang.blaze3d.platform.NativeImage albedoLevel = images[level];
            com.mojang.blaze3d.platform.NativeImage albedoUploadImage =
                buildAtlasUploadImage(sprite, albedoLevel, padding, level, currentFrameX,
                    currentFrameY, nextFrameX, nextFrameY, frameProgress);
            try {
                int atlasX = sprite.getX() >> level;
                int atlasY = sprite.getY() >> level;
                TextureUploadCompat.uploadPreparedAtlasAlbedoRegion(atlasTextureId,
                    albedoUploadImage, level, atlasX, atlasY);

                for (AuxiliaryTextures auxiliaryTexture : AuxiliaryTextures.values()) {
                    com.mojang.blaze3d.platform.NativeImage auxiliary =
                        auxiliaryTexture.copyPreparedImage(textureId, level);
                    if (auxiliary == null) {
                        int defaultValue = auxiliaryTexture.defaultValue(albedoLevel);
                        auxiliary = TextureCompat.mappedCopy(albedoLevel, ignored -> defaultValue);
                    }

                    com.mojang.blaze3d.platform.NativeImage auxiliaryUploadImage = null;
                    try {
                        auxiliaryUploadImage = buildAtlasUploadImage(sprite, auxiliary, padding,
                            level, currentFrameX, currentFrameY, nextFrameX, nextFrameY,
                            frameProgress);
                        TextureUploadCompat.uploadPreparedAtlasRegion(auxiliaryTexture,
                            atlasTextureId, albedoUploadImage, auxiliaryUploadImage, level,
                            atlasX, atlasY);
                    } finally {
                        if (auxiliaryUploadImage != null) {
                            auxiliaryUploadImage.close();
                        }
                        auxiliary.close();
                    }
                }
            } finally {
                albedoUploadImage.close();
            }
        }
    }

    private static com.mojang.blaze3d.platform.NativeImage buildAtlasUploadImage(
        TextureAtlasSprite sprite,
        com.mojang.blaze3d.platform.NativeImage source,
        int padding,
        int level,
        int currentFrameX,
        int currentFrameY,
        int nextFrameX,
        int nextFrameY,
        float frameProgress) {
        int spriteWidth = Math.max(1, sprite.contents().width() >> level);
        int spriteHeight = Math.max(1, sprite.contents().height() >> level);
        int uploadWidth = Math.max(1, (sprite.contents().width() + padding * 2) >> level);
        int uploadHeight = Math.max(1, (sprite.contents().height() + padding * 2) >> level);
        int leftPadding = Math.max(0, (padding) >> level);
        int topPadding = Math.max(0, (padding) >> level);
        com.mojang.blaze3d.platform.NativeImage upload =
            new com.mojang.blaze3d.platform.NativeImage(source.format(), uploadWidth,
                uploadHeight, false);

        int currentBaseX = currentFrameX * spriteWidth;
        int currentBaseY = currentFrameY * spriteHeight;
        int nextBaseX = nextFrameX >= 0 ? nextFrameX * spriteWidth : currentBaseX;
        int nextBaseY = nextFrameY >= 0 ? nextFrameY * spriteHeight : currentBaseY;

        for (int y = 0; y < uploadHeight; y++) {
            int sampleY = clamp(y - topPadding, 0, spriteHeight - 1);
            for (int x = 0; x < uploadWidth; x++) {
                int sampleX = clamp(x - leftPadding, 0, spriteWidth - 1);
                int current = readClamped(source, currentBaseX + sampleX, currentBaseY + sampleY);
                int color = current;
                if (nextFrameX >= 0 && nextFrameY >= 0 && frameProgress > 0.0F) {
                    int next = readClamped(source, nextBaseX + sampleX, nextBaseY + sampleY);
                    color = lerpColor(current, next, frameProgress);
                }
                TextureCompat.writeColor(upload, x, y, color);
            }
        }
        return upload;
    }

    private static int readClamped(com.mojang.blaze3d.platform.NativeImage source, int x, int y) {
        return TextureCompat.readColor(source, clamp(x, 0, source.getWidth() - 1),
            clamp(y, 0, source.getHeight() - 1));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int lerpColor(int a, int b, float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        return TextureCompat.argb(lerpChannel(aa, ba, clamped), lerpChannel(ar, br, clamped),
            lerpChannel(ag, bg, clamped), lerpChannel(ab, bb, clamped));
    }

    private static int lerpChannel(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }
}
