package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IGlyphAtlasTextureExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import net.minecraft.client.font.BakedGlyph;
import net.minecraft.client.font.GlyphAtlasTexture;
import net.minecraft.client.font.TextRenderLayerSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GlyphAtlasTexture.class)
public abstract class GlyphAtlasTextureMixins extends AbstractTextureMixins implements
    IGlyphAtlasTextureExt {

    @Final
    @Shadow
    private TextRenderLayerSet textRenderLayers;
    @Final
    @Shadow
    private boolean hasColor;
    @Final
    @Shadow
    private GlyphAtlasTexture.Slot rootSlot;

    @Override
    public BakedGlyph radiance$bake(IRenderableGlyphExt glyph) {
        if (glyph.hasColor() != this.hasColor) {
            return null;
        }
        GlyphAtlasTexture.Slot slot = this.rootSlot.findSlotFor(glyph);
        if (slot != null) {
            this.bindTexture();
            glyph.upload(this.getGlId(), slot.x, slot.y);
            float f = 256.0f;
            float g = 256.0f;
            float h = 0.01f;
            return new BakedGlyph(this.textRenderLayers,
                ((float) slot.x + 0.01f) / 256.0f,
                ((float) slot.x - 0.01f + (float) glyph.getWidth()) / 256.0f,
                ((float) slot.y + 0.01f) / 256.0f,
                ((float) slot.y - 0.01f + (float) glyph.getHeight()) / 256.0f,
                glyph.getXMin(),
                glyph.getXMax(),
                glyph.getYMin(),
                glyph.getYMax());
        }
        return null;
    }
}
