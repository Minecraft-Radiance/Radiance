package com.radiance.client.texture.compat;

import com.radiance.client.texture.AuxiliaryTextures;
import com.radiance.client.texture.EmissionRecorder;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
//#if MC<260200
import com.mojang.blaze3d.platform.TextureUtil;
import com.radiance.client.proxy.vulkan.TextureProxy;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
//#endif

public final class TextureUploadCompat {

    private TextureUploadCompat() {
    }

    public static void loadAndUpload(AuxiliaryTextures auxiliaryTexture, Object source,
        INativeImageExt sourceExt, int level, int offsetX, int offsetY, int unpackSkipPixels,
        int unpackSkipRows, int regionWidth, int regionHeight, boolean blur) {
        //#if MC>=260200
        //$$ return;
        //#else
        loadAndUploadLegacy(auxiliaryTexture, (NativeImage) source, sourceExt, level, offsetX,
            offsetY, unpackSkipPixels, unpackSkipRows, regionWidth, regionHeight, blur);
        //#endif
    }

    //#if MC<260200
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
        if (!auxiliaryTexture.glIdMapping().containsKey(targetId)) {
            auxiliaryTargetId = TextureProxy.generateTextureId();
            TextureUtil.prepareImage(texture.format().getNativeImageInternalFormat(),
                auxiliaryTargetId, texture.maxLayer(), texture.width(), texture.height());
            auxiliaryTexture.glIdMapping().put(targetId, auxiliaryTargetId);
        } else {
            auxiliaryTargetId = auxiliaryTexture.glIdMapping().get(targetId);

            TextureTracker.Texture auxiliaryTrackerTexture = TextureTracker.GLID2Texture.get(
                auxiliaryTargetId);
            if (texture.width() != auxiliaryTrackerTexture.width()
                || texture.height() != auxiliaryTrackerTexture.height()
                || texture.format() != auxiliaryTrackerTexture.format()) {
                TextureUtil.prepareImage(texture.format().getNativeImageInternalFormat(),
                    auxiliaryTargetId, texture.maxLayer(), texture.width(), texture.height());
            }
        }

        if (identifier.getPath().contains("textures/block") || identifier.getPath()
            .contains("textures/item") || identifier.getPath().contains("textures/entity")) {
            NativeImage preparedLevelCopy = auxiliaryTexture.copyPreparedImage(identifier, level);
            if (preparedLevelCopy != null) {
                auxiliaryTemplateImage = preparedLevelCopy;
            } else {
                int defaultValue = auxiliaryTexture.defaultValue(source);
                auxiliaryTemplateImage = source.applyToCopy(i -> defaultValue);
            }
        }

        if (auxiliaryTemplateImage == null) {
            return;
        }

        NativeImage auxiliaryImage = null;
        try {
            auxiliaryImage =
                ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) auxiliaryTemplateImage)
                    .radiance$alignTo(source);
            if (auxiliaryTemplateImage != auxiliaryImage) {
                auxiliaryTemplateImage.close();
            }

            ((INativeImageExt) (Object) auxiliaryImage).radiance$setTargetID(auxiliaryTargetId);

            if (auxiliaryImage.getWidth() != source.getWidth()
                || auxiliaryImage.getHeight() != source.getHeight()
                || auxiliaryImage.getFormat() != source.getFormat()) {
                throw new RuntimeException(
                    auxiliaryTexture.textureName() + " image size / format mismatch");
            }

            if (level == 0 && auxiliaryTexture == AuxiliaryTextures.SPECULAR) {
                long tileKey = EmissionRecorder.buildTileKey(offsetX, offsetY, regionWidth,
                    regionHeight);
                if (TextureProxy.hasEmissionTile(targetId, tileKey)) {
                    auxiliaryImage.upload(level, offsetX, offsetY, unpackSkipPixels,
                        unpackSkipRows, regionWidth, regionHeight, blur);
                    return;
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
    //#endif
}
