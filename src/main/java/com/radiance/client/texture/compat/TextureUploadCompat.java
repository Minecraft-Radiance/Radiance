package com.radiance.client.texture.compat;

import com.radiance.client.texture.AuxiliaryTextures;
import com.radiance.client.texture.EmissionRecorder;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.client.proxy.vulkan.TextureProxy;
//#if MC>=260200
//$$ import com.mojang.blaze3d.platform.NativeImage;
//$$ import net.minecraft.resources.Identifier;
//#else
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
//#endif

public final class TextureUploadCompat {

    private TextureUploadCompat() {
    }

    public static void loadAndUpload(AuxiliaryTextures auxiliaryTexture, Object source,
        INativeImageExt sourceExt, int level, int offsetX, int offsetY, int unpackSkipPixels,
        int unpackSkipRows, int regionWidth, int regionHeight, boolean blur) {
        loadAndUploadLegacy(auxiliaryTexture, (NativeImage) source, sourceExt, level, offsetX,
            offsetY, unpackSkipPixels, unpackSkipRows, regionWidth, regionHeight, blur);
    }

    public static void uploadAtlasSprite(AuxiliaryTextures auxiliaryTexture, int atlasTextureId,
        Identifier textureIdentifier, NativeImage source, int level, int atlasX, int atlasY) {
        INativeImageExt sourceExt = (INativeImageExt) (Object) source;
        sourceExt.radiance$setTargetID(atlasTextureId);
        sourceExt.radiance$setIdentifier(textureIdentifier);
        loadAndUploadLegacy(auxiliaryTexture, source, sourceExt, level, atlasX, atlasY, 0, 0,
            source.getWidth(), source.getHeight(), false);
    }

    public static void uploadPreparedAtlasRegion(AuxiliaryTextures auxiliaryTexture,
        int atlasTextureId, NativeImage albedoImage, NativeImage auxiliaryImage, int level,
        int atlasX, int atlasY) {
        Integer auxiliaryTargetId = auxiliaryTexture.glIdMapping().get(atlasTextureId);
        if (auxiliaryTargetId == null) {
            TextureTracker.Texture atlasTexture = TextureTracker.GLID2Texture.get(atlasTextureId);
            if (atlasTexture == null) {
                return;
            }
            auxiliaryTargetId = TextureProxy.generateTextureId();
            prepareImage(atlasTexture, auxiliaryTargetId);
            auxiliaryTexture.glIdMapping().put(atlasTextureId, auxiliaryTargetId);
            com.radiance.client.proxy.vulkan.BufferProxy.updateMapping();
        }

        int regionWidth = auxiliaryImage.getWidth();
        int regionHeight = auxiliaryImage.getHeight();
        if (level == 0 && auxiliaryTexture == AuxiliaryTextures.SPECULAR) {
            long tileKey = EmissionRecorder.buildTileKey(atlasX, atlasY, regionWidth,
                regionHeight);
            if (!TextureProxy.hasEmissionTile(atlasTextureId, tileKey)) {
                TextureProxy.uploadEmissionTile(EmissionRecorder.buildTileUpdate(atlasTextureId,
                    albedoImage, auxiliaryImage, atlasX, atlasY, 0, 0, regionWidth,
                    regionHeight));
            }
        }

        upload(auxiliaryImage, level, atlasX, atlasY, 0, 0, regionWidth, regionHeight, false,
            auxiliaryTargetId);
    }

    public static void uploadPreparedAtlasAlbedoRegion(int atlasTextureId, NativeImage albedoImage,
        int level, int atlasX, int atlasY) {
        if (!TextureTracker.GLID2Texture.containsKey(atlasTextureId)) {
            return;
        }
        upload(albedoImage, level, atlasX, atlasY, 0, 0, albedoImage.getWidth(),
            albedoImage.getHeight(), false, atlasTextureId);
    }

    private static void loadAndUploadLegacy(AuxiliaryTextures auxiliaryTexture, NativeImage source,
        INativeImageExt sourceExt, int level, int offsetX, int offsetY, int unpackSkipPixels,
        int unpackSkipRows, int regionWidth, int regionHeight, boolean blur) {
        int targetId = sourceExt.radiance$getTargetID();
        Identifier identifier = sourceExt.radiance$getIdentifier();
        if (identifier == null || AuxiliaryTextures.isAuxiliaryTexture(identifier)) {
            return;
        }

        NativeImage auxiliaryTemplateImage = null;
        int auxiliaryTargetId;
        TextureTracker.Texture texture = TextureTracker.GLID2Texture.get(targetId);
        if (texture == null) {
            return;
        }
        if (!auxiliaryTexture.glIdMapping().containsKey(targetId)) {
            auxiliaryTargetId = TextureProxy.generateTextureId();
            prepareImage(texture, auxiliaryTargetId);
            auxiliaryTexture.glIdMapping().put(targetId, auxiliaryTargetId);
            com.radiance.client.proxy.vulkan.BufferProxy.updateMapping();
        } else {
            auxiliaryTargetId = auxiliaryTexture.glIdMapping().get(targetId);

            TextureTracker.Texture auxiliaryTrackerTexture = TextureTracker.GLID2Texture.get(
                auxiliaryTargetId);
            if (auxiliaryTrackerTexture == null
                || texture.width() != auxiliaryTrackerTexture.width()
                || texture.height() != auxiliaryTrackerTexture.height()
                || texture.format() != auxiliaryTrackerTexture.format()) {
                prepareImage(texture, auxiliaryTargetId);
                com.radiance.client.proxy.vulkan.BufferProxy.updateMapping();
            }
        }

        if (identifier.getPath().contains("textures/block") || identifier.getPath()
            .contains("textures/item") || identifier.getPath().contains("textures/entity")) {
            NativeImage preparedLevelCopy = auxiliaryTexture.copyPreparedImage(identifier, level);
            if (preparedLevelCopy != null) {
                auxiliaryTemplateImage = preparedLevelCopy;
            } else {
                int defaultValue = auxiliaryTexture.defaultValue(source);
                auxiliaryTemplateImage = TextureCompat.mappedCopy(source, i -> defaultValue);
            }
        }

        if (auxiliaryTemplateImage == null) {
            return;
        }

        NativeImage auxiliaryImage = null;
        try {
            auxiliaryImage = TextureCompat.alignTo(auxiliaryTemplateImage, source);
            if (auxiliaryTemplateImage != auxiliaryImage) {
                auxiliaryTemplateImage.close();
            }

            ((INativeImageExt) (Object) auxiliaryImage).radiance$setTargetID(auxiliaryTargetId);

            if (auxiliaryImage.getWidth() != source.getWidth()
                || auxiliaryImage.getHeight() != source.getHeight()
                || TextureCompat.format(auxiliaryImage) != TextureCompat.format(source)) {
                throw new RuntimeException(
                    auxiliaryTexture.textureName() + " image size / format mismatch");
            }

            if (level == 0 && auxiliaryTexture == AuxiliaryTextures.SPECULAR) {
                long tileKey = EmissionRecorder.buildTileKey(offsetX, offsetY, regionWidth,
                    regionHeight);
                if (TextureProxy.hasEmissionTile(targetId, tileKey)) {
                    upload(auxiliaryImage, level, offsetX, offsetY, unpackSkipPixels,
                        unpackSkipRows, regionWidth, regionHeight, blur, auxiliaryTargetId);
                    return;
                }

                TextureProxy.uploadEmissionTile(EmissionRecorder.buildTileUpdate(targetId,
                    source, auxiliaryImage, offsetX, offsetY, unpackSkipPixels,
                    unpackSkipRows, regionWidth, regionHeight));
            }

            upload(auxiliaryImage, level, offsetX, offsetY, unpackSkipPixels, unpackSkipRows,
                regionWidth, regionHeight, blur, auxiliaryTargetId);
        } finally {
            if (auxiliaryImage != null) {
                auxiliaryImage.close();
            }
        }
    }

    private static void prepareImage(TextureTracker.Texture texture, int textureId) {
        //#if MC>=260200
        //$$ TextureProxy.prepareImage(textureId, texture.maxLayer(), texture.width(),
        //$$     texture.height(), texture.format());
        //#else
        TextureUtil.prepareImage(texture.format().getNativeImageInternalFormat(), textureId,
            texture.maxLayer(), texture.width(), texture.height());
        //#endif
        TextureTracker.GLID2Texture.put(textureId, texture);
    }

    private static void upload(NativeImage image, int level, int offsetX, int offsetY,
        int unpackSkipPixels, int unpackSkipRows, int regionWidth, int regionHeight, boolean blur,
        int targetId) {
        //#if MC>=260200
        //$$ TextureProxy.queueUpload(TextureCompat.pointer(image), TextureCompat.byteSize(image),
        //$$     image.getWidth(), targetId, unpackSkipPixels, unpackSkipRows, offsetX, offsetY,
        //$$     regionWidth, regionHeight, level);
        //#else
        image.upload(level, offsetX, offsetY, unpackSkipPixels, unpackSkipRows, regionWidth,
            regionHeight, blur);
        //#endif
    }
}
