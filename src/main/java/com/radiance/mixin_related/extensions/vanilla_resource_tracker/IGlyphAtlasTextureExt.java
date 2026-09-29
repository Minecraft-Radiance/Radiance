package com.radiance.mixin_related.extensions.vanilla_resource_tracker;

import net.minecraft.client.font.GlyphRenderer;

public interface IGlyphAtlasTextureExt {

    GlyphRenderer radiance$getGlyphRenderer(IRenderableGlyphExt glyph);
}
