package com.radiance.client.blaze3d.scene;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.client.blaze3d.backend.RadianceBackendHandles;
import com.radiance.client.texture.TextureTracker;
import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

public final class TextureAtlasSync {

    private TextureAtlasSync() {
    }

    public static void sync(TextureAtlas atlas, Map<Identifier, TextureAtlasSprite> sprites) {
        GpuTexture texture;
        GpuTextureView textureView;
        try {
            texture = atlas.getTexture();
            textureView = atlas.getTextureView();
        } catch (IllegalStateException ignored) {
            return;
        }

        long atlasTexture = RadianceBackendHandles.textureHandle(texture);
        long atlasTextureView = RadianceBackendHandles.textureViewHandle(textureView);
        if (atlasTexture == 0L) {
            return;
        }

        TextureTracker.clearAtlas(atlasTexture);
        for (Map.Entry<Identifier, TextureAtlasSprite> entry : sprites.entrySet()) {
            TextureAtlasSprite sprite = entry.getValue();
            TextureTracker.syncAtlasSprite(atlasTexture, atlasTextureView, atlas.location(),
                entry.getKey(), sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(),
                sprite.getX(), sprite.getY(), sprite.contents().width(), sprite.contents().height());
        }
    }
}
