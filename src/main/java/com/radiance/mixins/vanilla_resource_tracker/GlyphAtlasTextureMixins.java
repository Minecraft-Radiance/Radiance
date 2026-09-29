package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IGlyphAtlasTextureExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(FontTexture.class)
public abstract class GlyphAtlasTextureMixins extends AbstractTextureMixins implements
    IGlyphAtlasTextureExt {

    @Final
    @Shadow
    private GlyphRenderTypes renderTypes;
    @Final
    @Shadow
    private boolean colored;
    @Final
    @Shadow
    private FontTexture.Node root;

    @Override
    public BakedGlyph radiance$getGlyphRenderer(IRenderableGlyphExt glyph) {
        if (glyph.isColored() != this.colored) {
            return null;
        }
        FontTexture.Node slot = this.root.insert(glyph);
        if (slot != null) {
            this.bind();
            glyph.upload(this.getId(), slot.x, slot.y);
            return new BakedGlyph(this.renderTypes,
                ((float) slot.x + 0.01f) / 256.0f,
                ((float) slot.x - 0.01f + (float) glyph.getPixelWidth()) / 256.0f,
                ((float) slot.y + 0.01f) / 256.0f,
                ((float) slot.y - 0.01f + (float) glyph.getPixelHeight()) / 256.0f,
                glyph.getLeft(),
                glyph.getRight(),
                glyph.getUp(),
                glyph.getDown());
        }
        return null;
    }
}
